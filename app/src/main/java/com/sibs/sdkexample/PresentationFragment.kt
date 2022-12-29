package com.sibs.sdkexample

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.sibs.sdk.PaymentMethod
import com.sibs.sdk.SibsSdkError
import com.sibs.sdk.TransferResult

private val defaultRequiredParams = mapOf(
    "terminalId" to "182",
    "transactionId" to "Order Id: nory709fa4",
    "transactionDescription" to "transaction description test",
    "amount" to "50.5",
    "currency" to "PLN"
)
private val defaultOptionalParams = mapOf(
    "merchantTransactionDescription" to "merchant transaction description",
    "client" to "Chuck Norris",
    "email" to "chuck@norris.com",
    "shopUrl" to "https://chucknorris.com"
)

class PresentationFragment : Fragment() {

    interface Callbacks {
        fun startPayment(
            stringParams: Map<String, String>,
            paymentMethodsParams: List<PaymentMethod>
        )
    }

    private lateinit var linearLayout: LinearLayout
    private lateinit var inputTexts: MutableMap<String, String>
    private lateinit var inputPaymentMethods: MutableMap<PaymentMethod, Boolean>
    private var result: TransferResult? = null

    //OS
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        inputTexts = getTextInputs(savedInstanceState)
        inputPaymentMethods = getPaymentMethods(savedInstanceState)
        result = savedInstanceState?.getSerializableCompat(TRANSFER_RESULT_KEY)
        return NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            linearLayout = LinearLayout(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16.pxAsDp(), 0, 16.pxAsDp(), 16.pxAsDp())
                orientation = LinearLayout.VERTICAL
            }
            addView(linearLayout)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        inputTexts.forEach { outState.putString(it.key, it.value) }
        inputPaymentMethods
            .mapNotNull { (paymentMethod, isSelected) -> if (isSelected) paymentMethod.name else null }
            .let { outState.putStringArray(TRANSFER_PAYMENT_METHODS_KEY, it.toTypedArray()) }
    }

    override fun onResume() {
        super.onResume()
        result?.let(::setupResultViews) ?: setupInputViews(inputTexts, inputPaymentMethods)
    }

    fun submitResult(result: TransferResult) {
        this.result = result
        setupResultViews(result)
    }

    //Views helpers
    private fun setupInputViews(
        inputTexts: MutableMap<String, String>,
        inputPaymentMethods: MutableMap<PaymentMethod, Boolean>
    ) = with(linearLayout) {
        removeAllViews()
        addView(createHeaderView("Required params"))
        defaultRequiredParams.keys.sorted().forEach { hint ->
            inputTexts[hint]?.let { input ->
                addView(
                    createTextView(
                        text = input,
                        hint = hint,
                        onTextChanged = { inputTexts[hint] = it },
                    )
                )
            }
        }
        addView(TextView(requireContext()).apply {
            text = "Payment methods"
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 16.pxAsDp(), 0, 0) }
        })
        PaymentMethod.values().forEach { paymentMethod ->
            addView(
                createCheckboxView(
                    paymentMethod = paymentMethod,
                    isChecked = inputPaymentMethods[paymentMethod]!!,
                    onCheckedChange = {
                        inputPaymentMethods[paymentMethod] = it
                    },
                )
            )
        }
        addView(createHeaderView("Optional params"))
        defaultOptionalParams.keys.sorted().forEach { hint ->
            inputTexts[hint]?.let { input ->
                addView(
                    createTextView(
                        text = input,
                        hint = hint,
                        onTextChanged = { newInput -> inputTexts[hint] = newInput },
                    ),
                )
            }
        }
        addView(createButtonView("Start SDK") {
            (requireActivity() as? Callbacks)?.startPayment(
                stringParams = inputTexts,
                paymentMethodsParams = inputPaymentMethods.mapNotNull { if (it.value) it.key else null }
            )
        })
    }

    private fun setupResultViews(result: TransferResult) = with(linearLayout) {
        removeAllViews()
        addView(
            createTextView(
                text = result.isSuccess.toString(),
                hint = "Payment result",
            )
        )
        result.transactionId?.let { transactionId ->
            addView(createTextView(text = transactionId, hint = "transactionId"))
        }
        result.sdkError?.let { sdkError ->
            var text = sdkError::class.java.simpleName
            if(sdkError is SibsSdkError.CheckoutError) {
                text += ":${sdkError.httpErrorCode}"
            }
            addView(createTextView(text = text, hint = "sdk error"))
        }
        addView(createButtonView(getString(android.R.string.ok)) {
            this@PresentationFragment.result = null
            setupInputViews(inputTexts, inputPaymentMethods)
        })
    }

    private fun createHeaderView(header: String): View {
        return TextView(requireContext()).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 16.pxAsDp(), 0, 0) }
            text = header.uppercase()
            gravity = Gravity.END
            setTypeface(null, Typeface.BOLD)
        }
    }

    private fun createTextView(
        text: String,
        hint: String,
        onTextChanged: ((String) -> Unit)? = null
    ): View {
        return layoutInflater.inflate(R.layout.item, linearLayout, false).apply {
            findViewById<TextView>(R.id.hint).text = hint
            findViewById<EditText>(R.id.input).run {
                isEnabled = false
                setText(text)
                tag = hint
                afterTextChanged { onTextChanged?.invoke(it) }
                isEnabled = onTextChanged != null
            }
        }
    }

    private fun createButtonView(text: String, clickListener: View.OnClickListener): View {
        return Button(requireContext()).apply {
            setOnClickListener(clickListener)
            layoutParams = createItemLayoutParams()
                .apply { setMargins(0, 32, 0, 16) }
            setText(text)
        }
    }

    private fun createCheckboxView(
        paymentMethod: PaymentMethod,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ): View {
        return CheckBox(requireContext()).apply {
            layoutParams = createItemLayoutParams()
            text = paymentMethod.name
            this.isChecked = isChecked
            setOnCheckedChangeListener { _, isChecked -> onCheckedChange(isChecked) }
        }
    }

    private fun createItemLayoutParams() = ViewGroup.MarginLayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )


    //Saved instance state helpers
    private fun getTextInputs(bundle: Bundle? = null) = mutableMapOf<String, String>().apply {
        defaultRequiredParams.entries.forEach {
            put(it.key, bundle?.getString(it.key) ?: it.value)
        }
        defaultOptionalParams.entries.forEach {
            put(it.key, bundle?.getString(it.key) ?: it.value)
        }
    }

    private fun getPaymentMethods(bundle: Bundle? = null): MutableMap<PaymentMethod, Boolean> {
        val storedPaymentMethods = bundle
            ?.getStringArray(TRANSFER_PAYMENT_METHODS_KEY)
            ?.mapNotNull { name -> PaymentMethod.values().find { pm -> pm.name == name } }
            .orEmpty()
        return mutableMapOf<PaymentMethod, Boolean>().apply {
            PaymentMethod.values().forEach { paymentMethod ->
                val isSelected = if (bundle == null) true else {
                    storedPaymentMethods.contains(paymentMethod)
                }
                put(paymentMethod, isSelected)
            }
        }
    }

    companion object {
        const val TRANSFER_RESULT_KEY = "result"
        const val TRANSFER_PAYMENT_METHODS_KEY = "payment_methods"
    }
}