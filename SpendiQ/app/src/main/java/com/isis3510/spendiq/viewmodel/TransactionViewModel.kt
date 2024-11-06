package com.isis3510.spendiq.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isis3510.spendiq.model.data.Transaction
import com.isis3510.spendiq.model.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * TransactionViewModel class
 *
 * This ViewModel manages the user's financial transactions, providing functionalities to fetch, add, update,
 * and delete transactions from a repository. It uses Kotlin Coroutines to handle asynchronous operations
 * with the data layer, specifically interfacing with Firestore or any other data source through the
 * TransactionRepository.
 *
 * Key Features:
 * - Reactive Data Management: Exposes transaction data and UI states as StateFlow objects for the UI to observe.
 * - CRUD Operations: Provides functions to create, read, update, and delete transactions.
 * - Error Handling: Manages error states and logs errors for debugging.
 * - Income and Expense Calculation: Offers functionality to compute total income and expenses from the transaction list.
 *
 * State Management:
 * - `_transactions`: MutableStateFlow containing a list of transactions.
 * - `transactions`: Public immutable StateFlow for observing transactions.
 * - `_selectedTransaction`: MutableStateFlow for managing the currently selected transaction.
 * - `selectedTransaction`: Public immutable StateFlow for observing the selected transaction.
 * - `_uiState`: MutableStateFlow for tracking the current UI state (Idle, Loading, Success, or Error).
 * - `uiState`: Public immutable StateFlow for observing UI state changes.
 *
 * Initialization:
 * - On instantiation, the ViewModel initializes the transaction repository and prepares to fetch transactions.
 *
 * Transaction Management Functions:
 * - `fetchTransactions(accountName: String)`: Fetches transactions for a specified account and updates the UI state.
 * - `getTransaction(accountId: String, transactionId: String)`: Retrieves a specific transaction and updates the selected transaction state.
 * - `addTransactionWithAccountCheck(transaction: Transaction)`: Adds a new transaction and refreshes the transaction list.
 * - `updateTransaction(accountId: String, oldTransaction: Transaction, newTransaction: Transaction)`: Updates an existing transaction.
 * - `deleteTransaction(accountId: String, transaction: Transaction)`: Deletes a transaction and refreshes the transaction list.
 * - `getIncomeAndExpenses()`: Calculates and returns the total income and expenses from the current transaction list.
 * - `fetchAllTransactions()`: Fetches all transactions for the authenticated user and updates the transaction list.
 *
 * UI State Management:
 * - Sealed class `UiState` defines the possible UI states (Idle, Loading, Success, and Error) for better state handling.
 *
 * Error Handling:
 * - Logs errors encountered during transaction operations for easier debugging.
 */
class TransactionViewModel(
    private val transactionRepository: TransactionRepository = TransactionRepository()
) : ViewModel() {
    // MutableStateFlow for transactions
    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())

    // Public immutable StateFlow for transactions
    val transactions: StateFlow<List<Transaction>> = _transactions

    // MutableStateFlow for the selected transaction
    private val _selectedTransaction = MutableStateFlow<Transaction?>(null)

    // Public immutable StateFlow for the selected transaction
    val selectedTransaction: StateFlow<Transaction?> = _selectedTransaction

    // MutableStateFlow for the UI state
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)

    // Public immutable StateFlow for the UI state
    val uiState: StateFlow<UiState> = _uiState

    // Fetch transactions for a specific account
    fun fetchTransactions(accountName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.getTransactions(accountName).collect { result ->
                _uiState.value = when {
                    result.isSuccess -> {
                        _transactions.value = result.getOrNull() ?: emptyList()
                        UiState.Success
                    }
                    result.isFailure -> {
                        UiState.Error(result.exceptionOrNull()?.message ?: "Failed to fetch transactions")
                    }
                    else -> UiState.Error("Unexpected error")
                }
            }
        }
    }

    // Get details for a specific transaction
    fun getTransaction(accountId: String, transactionId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.getTransaction(accountId, transactionId).collect { result ->
                when {
                    result.isSuccess -> {
                        _selectedTransaction.value = result.getOrNull()
                        _uiState.value = UiState.Success
                    }
                    result.isFailure -> {
                        _uiState.value = UiState.Error(result.exceptionOrNull()?.message ?: "Failed to get transaction")
                        _selectedTransaction.value = null
                    }
                }
            }
        }
    }

    // Add a transaction with a check on account
    fun addTransactionWithAccountCheck(transaction: Transaction) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.addTransaction(transaction).collect { result ->
                _uiState.value = when {
                    result.isSuccess -> {
                        // Refresh the transactions list for the current account
                        fetchTransactions(transaction.accountId)
                        UiState.Success
                    }
                    result.isFailure -> {
                        UiState.Error(result.exceptionOrNull()?.message ?: "Failed to add transaction")
                    }
                    else -> UiState.Error("Unexpected error")
                }
            }
        }
    }

    // Update a transaction
    fun updateTransaction(accountId: String, oldTransaction: Transaction, newTransaction: Transaction) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.updateTransaction(accountId, oldTransaction, newTransaction).collect { result ->
                _uiState.value = when {
                    result.isSuccess -> {
                        _selectedTransaction.value = newTransaction
                        fetchTransactions(newTransaction.accountId) // Refresh transactions list
                        UiState.Success
                    }
                    result.isFailure -> {
                        UiState.Error(result.exceptionOrNull()?.message ?: "Failed to update transaction")
                    }
                    else -> UiState.Error("Unexpected error")
                }
            }
        }
    }

    // Delete a transaction
    fun deleteTransaction(accountId: String, transaction: Transaction) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.deleteTransaction(accountId, transaction).collect { result ->
                _uiState.value = when {
                    result.isSuccess -> {
                        _selectedTransaction.value = null
                        fetchTransactions(transaction.accountId) // Refresh transactions list
                        UiState.Success
                    }
                    result.isFailure -> {
                        UiState.Error(result.exceptionOrNull()?.message ?: "Failed to delete transaction")
                    }
                    else -> UiState.Error("Unexpected error")
                }
            }
        }
    }

    // Calculate total income and expenses
    fun getIncomeAndExpenses(): Pair<Long, Long> {
        var totalIncome = 0L
        var totalExpenses = 0L

        transactions.value.forEach { transaction ->
            if (transaction.transactionType == "Income") {
                totalIncome += transaction.amount
            } else if (transaction.transactionType == "Expense") {
                totalExpenses += transaction.amount
            }
        }

        Log.d("TransactionViewModel", "Expenses: $totalExpenses")
        Log.d("TransactionViewModel", "Income: $totalIncome")

        return Pair(totalIncome, totalExpenses)
    }

    // Fetch all transactions
    fun fetchAllTransactions() {
        Log.d("TransactionViewModel", "fetchAllTransactions called")
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            transactionRepository.getAllTransactions().collect { result ->
                _uiState.value = when {
                    result.isSuccess -> {
                        val transactionsList = result.getOrNull() ?: emptyList()
                        _transactions.value = transactionsList

                        Log.d("TransactionViewModel", "Transactions obtained: $transactionsList")

                        UiState.Success
                    }
                    result.isFailure -> {
                        Log.e("TransactionViewModel", "Error fetching transactions: ${result.exceptionOrNull()?.message}")
                        UiState.Error(result.exceptionOrNull()?.message ?: "Failed to fetch transactions")
                    }
                    else -> UiState.Error("Unexpected error")
                }
            }
        }
    }

    // Clear the selected transaction
    fun clearSelectedTransaction() {
        _selectedTransaction.value = null
    }

    // Sealed class for UI state management
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
}
