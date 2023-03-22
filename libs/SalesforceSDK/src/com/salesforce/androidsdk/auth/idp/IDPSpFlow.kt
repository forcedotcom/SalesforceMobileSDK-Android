package com.salesforce.androidsdk.auth.idp

/**
 * Super class of classes that are used to capture request/response and other state
 * during IDP/SP flows
 */
internal sealed class IDPSpFlow() {
    class IDPInitiatedFlow():IDPSpFlow() {
        lateinit var idpLoginRequest: IDPSPMessage.IDPLoginRequest
        lateinit var spLoginRequest: IDPSPMessage.SPLoginRequest
        lateinit var spLoginResponse: IDPSPMessage.SPLoginResponse
        lateinit var idpLoginResponse: IDPSPMessage.IDPLoginResponse
    }

    class SPInitiatedFlow():IDPSpFlow() {
        lateinit var spLoginRequest: IDPSPMessage.SPLoginRequest
        lateinit var spLoginResponse: IDPSPMessage.SPLoginResponse
    }
}