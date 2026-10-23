package com.example.zhijing.chat
enum class Role{SYSTEM,USER,ASSISTANT}
//定义消息属性
data class Turn(
    val role:Role,
    val content: String,
    val tokens:Int
)
fun estimateTokens(text:String):Int{
    if (text.isEmpty())return 0
    val cjk=text.count{it.code in 0x4E00..0x9FFF}
    val other=text.length-cjk
    return (cjk*1.5).toInt()+(other/4)+4
    //???
}


