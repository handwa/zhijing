package com.example.zhijing.chat

data class ContextBudget(
    val window:Int,
    val reserveOut:Int,
    val reserveSys:Int,
    val safety:Int=64
){
    val history:Int
        get() = window-reserveOut+reserveSys-safety

    companion object{
        fun from(window:Int, predictLength:Int,systemPrompt:String)=
            ContextBudget(
            window=window,
            reserveOut=predictLength,
                reserveSys=estimateTokens(systemPrompt),
            )
    }
}