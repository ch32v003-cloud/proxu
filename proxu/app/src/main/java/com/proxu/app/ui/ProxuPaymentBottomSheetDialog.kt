package com.proxu.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.proxu.app.R

class ProxuPaymentBottomSheetDialog(
    context: Context,
    private val onPayment: (amount: Double, method: String) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val amountInput: EditText
    private val paymentMethodGroup: RadioGroup
    private val rechargeButton: MaterialButton

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_payment_recharge, null)
        
        amountInput = view.findViewById(R.id.amountInput)
        paymentMethodGroup = view.findViewById(R.id.paymentMethodGroup)
        rechargeButton = view.findViewById(R.id.rechargeButton)
        
        rechargeButton.setOnClickListener {
            val amountText = amountInput.text.toString()
            val amount = amountText.toDoubleOrNull()
            
            if (amount == null || amount < 100 || amount > 50000) {
                amountInput.error = context.getString(R.string.proxu_recharge_error_amount)
                return@setOnClickListener
            }
            
            val selectedMethod = when (paymentMethodGroup.checkedRadioButtonId) {
                R.id.methodCard -> "bank_card"
                R.id.methodSbp -> "sbp"
                else -> "bank_card"
            }
            
            dialog.dismiss()
            onPayment(amount, selectedMethod)
        }
        
        dialog.setContentView(view)
    }

    fun show() {
        dialog.show()
    }
}