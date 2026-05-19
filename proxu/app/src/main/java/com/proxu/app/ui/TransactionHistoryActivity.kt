package com.proxu.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.proxu.app.R
import com.proxu.app.auth.ProxuApiService
import com.proxu.app.auth.ProxuAuthManager
import com.proxu.app.databinding.ActivityTransactionHistoryBinding
import com.proxu.app.extension.toast
import com.proxu.app.extension.toastError
import com.proxu.app.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryActivity : BaseActivity() {
    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, true, getString(R.string.proxu_transaction_history))

        adapter = TransactionAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadTransactions()
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            binding.emptyText.visibility = View.GONE
            
            try {
                val token = ProxuAuthManager.getToken(this@TransactionHistoryActivity)
                if (token.isNullOrBlank()) {
                    toast(R.string.auth_login_required)
                    finish()
                    return@launch
                }

                val transactions = withContext(Dispatchers.IO) {
                    ProxuApiService.getTransactions(token)
                }

                if (transactions == null || transactions.length() == 0) {
                    binding.emptyText.visibility = View.VISIBLE
                    adapter.setData(emptyList())
                } else {
                    val list = mutableListOf<TransactionItem>()
                    for (i in 0 until transactions.length()) {
                        val obj = transactions.optJSONObject(i)
                        if (obj != null) {
                            list.add(parseTransaction(obj))
                        }
                    }
                    adapter.setData(list)
                }
            } catch (e: Exception) {
                LogUtil.e("TransactionHistory", "Failed to load transactions", e)
                toastError(R.string.toast_failure)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun parseTransaction(json: JSONObject): TransactionItem {
        val type = json.optString("type", "")
        val amount = json.optDouble("amount", 0.0)
        val description = json.optString("description", "")
        val createdAt = json.optString("created_at", "")
        val paymentStatus = json.optString("payment_status", "")
        
        return TransactionItem(
            type = type,
            amount = amount,
            description = description,
            createdAt = formatDate(createdAt),
            isPositive = amount > 0,
            status = paymentStatus
        )
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    data class TransactionItem(
        val type: String,
        val amount: Double,
        val description: String,
        val createdAt: String,
        val isPositive: Boolean,
        val status: String
    )

    class TransactionAdapter : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
        private var data = listOf<TransactionItem>()

        fun setData(newData: List<TransactionItem>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transaction, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(data[position])
        }

        override fun getItemCount() = data.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvType: TextView = itemView.findViewById(R.id.tv_type)
            private val tvAmount: TextView = itemView.findViewById(R.id.tv_amount)
            private val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
            private val tvDate: TextView = itemView.findViewById(R.id.tv_date)
            private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)

            fun bind(item: TransactionItem) {
                val context = itemView.context
                
                tvType.text = when (item.type) {
                    "deposit" -> "Пополнение"
                    "daily_billing" -> "Списание"
                    "daily_charge" -> "Списание"
                    else -> item.type
                }
                
                val amountText = if (item.isPositive) "+${item.amount}" else "${item.amount}"
                tvAmount.text = "${amountText} руб."
                tvAmount.setTextColor(
                    if (item.isPositive) 
                        context.getColor(R.color.colorPing) 
                    else 
                        context.getColor(R.color.md_theme_error)
                )
                
                tvDescription.text = item.description
                tvDate.text = item.createdAt
                
                if (item.status.isNotBlank() && item.status != "completed") {
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = item.status
                } else {
                    tvStatus.visibility = View.GONE
                }
            }
        }
    }
}