package com.sibs.sdkexample

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sibs.sdk.*
import kotlinx.coroutines.launch

class SdkConsumerActivity : AppCompatActivity(), PresentationFragment.Callbacks {

    private lateinit var tokensCache: MutableSet<Token>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        tokensCache = savedInstanceState
            ?.getSerializableArrayListCompat<Token>(TOKENS_CACHE_KEY)
            .orEmpty()
            .toMutableSet()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(
            TOKENS_CACHE_KEY,
            arrayListOf<Token>().apply { addAll(tokensCache) })
    }

    override fun startPayment(
        stringParams: Map<String, String>,
        paymentMethodsParams: List<PaymentMethod>,
        useCardTokenization: Boolean,
        tokens: List<Token>,
    ) {
        /** 1. Create Transaction Params **/
        val transactionParams = createTransactionParams(
            stringParams,
            paymentMethodsParams,
            useCardTokenization,
            tokens
        ) ?: return

        /** 2. Start SIBS SDK **/
        sdkActivityLauncher.launch(
            TransactionActivity.getIntent(
                this,
                transactionParams,
                SibsSDKConfig.fromMetadataOrNull(this)!!
            )
        )
    }

    /** 3. Consume the result **/
    private val sdkActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val transferResult = activityResult.data
            ?.let(TransactionActivity::parseResult)
            ?: return@registerForActivityResult

        if (Activity.RESULT_OK == activityResult.resultCode) {
            showToast("Transaction finished")

            val presentationFragment =
                (supportFragmentManager.fragments.firstOrNull() as? PresentationFragment)

            presentationFragment?.submitResult(transferResult)

            /** Card Tokenization: Cache token  **/
            transferResult.token?.let { presentationFragment?.cacheToken(it) }

        } else if (Activity.RESULT_CANCELED == activityResult.resultCode) {
            showToast("Transaction canceled")
        }

        /** TransactionId will be available only if the transaction got registered in Sibs **/
        transferResult.transactionId?.let(::performOptionalTransactionStatusCheck)
    }

    /**
     * 4. Optional. This step can be performed to check detailed transaction status based on @transactionId.
     * @transactionId will be available only if transaction got registered in Sibs
     *
     * Example use case: The user canceled the transaction before the SDK managed to confirm its status.
     *
     * This check will perform the call to check current status of the transaction
     **/
    private fun performOptionalTransactionStatusCheck(transactionId: String) {
        val statusCheckService = SibsPaymentStatusService(SibsSDKConfig.fromMetadataOrNull(this)!!)
        lifecycleScope.launch {
            when (val response = statusCheckService.check(transactionId)) {
                is PaymentStatusCheckResponse.Checked -> showToast("Current status: ${response.paymentStatus.paymentStatus}")
                is PaymentStatusCheckResponse.Error -> showToast("${response::class.java.simpleName}:${response.httpErrorCode}")
                PaymentStatusCheckResponse.SDKNotConfigured -> showToast(response::class.java.simpleName)
            }
        }
    }

    private fun createTransactionParams(
        stringParams: Map<String, String>,
        paymentMethods: List<PaymentMethod>,
        useCardTokenization: Boolean,
        tokens: List<Token>
    ): TransactionParams? {
        val terminalId = stringParams["terminalId"]?.toIntOrNull() ?: 0
        val merchantTransactionDescription =
            stringParams["merchantTransactionDescription"].orEmpty()
        val transactionId = stringParams["transactionId"].orEmpty()
        val transactionDescription = stringParams["transactionDescription"].orEmpty()
        val amount = stringParams["amount"]?.toDoubleOrNull() ?: 0.0
        val currency = stringParams["currency"].orEmpty()
        val client = stringParams["client"].orEmpty()
        val email = stringParams["email"].orEmpty()
        val shopUrl = stringParams["shopUrl"].orEmpty()

        val addressBuilder = AddressParams.Builder()
            .street1("Wall Street")
            .city("Częstochowa")
            .zip("42-200")
            .country("PL")

        val shippingAddress = runCatching { addressBuilder.street2("shipping").build() }
            .onFailure { showErrorToast("Shipping address params", it) }
            .getOrNull()
        val billingAddress = runCatching { addressBuilder.street2("billing").build() }
            .onFailure { showErrorToast("Billing address params", it) }
            .getOrNull()


        val transactionParamsBuilder = TransactionParams.Builder()
            //required parameters
            .terminalId(terminalId)
            .transactionDescription(transactionDescription)
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .paymentMethods(paymentMethods)
            //optional card tokenization
            .apply {
                if (useCardTokenization) {
                    val cachedCardTokens = tokensCache.takeIf { it.isNotEmpty() }?.toList()
                    val tokenizationParams = TokenizationParams.Builder()
                        .tokenizeCard(true)
                        .tokens(cachedCardTokens)
                        .build()
                    tokenization(tokenizationParams)
                }
            }
            //optional parameters
            .merchantTransactionDescription(merchantTransactionDescription)
            .shopUrl(shopUrl)
            .client(client)
            .email(email)
            .billingAddress(billingAddress)
            .shippingAddress(shippingAddress)

        return runCatching { transactionParamsBuilder.build() }
            .onFailure { showErrorToast("Couldn't build the transaction params", it) }
            .getOrNull()
    }

    private fun showErrorToast(msg: String, cause: Throwable) =
        showToast("$msg: [${cause.message}]")

    private fun showToast(msg: String) = Toast
        .makeText(this, msg, Toast.LENGTH_SHORT)
        .show()

    companion object {
        private const val TOKENS_CACHE_KEY = "tokens_cache"
    }
}