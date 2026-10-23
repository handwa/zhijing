package com.example.zhijing.chat

class ContextCompressor(private val budget: ContextBudget) {
    fun trim(history:List<Turn>): List<Turn>{
        var used=0
        val kept=mutableListOf<Turn>()
        for(turn in history.asReversed()){
            if(used+turn.tokens>budget.history)break
            kept.add(0,turn)
            used+=turn.tokens
        }
        while(kept.isNotEmpty()&&kept.first().role==Role.ASSISTANT){
            kept.removeAt(0)
        }
        return kept
    }
    fun extractFacts(dropped:List<Turn>):String=
        dropped.mapNotNull{turn->
            when(turn.role){
                Role.USER -> "用户提到" + turn.content.take(30)
                Role.ASSISTANT -> "助手回复要点" + turn.content.lineSequence().first().take(30)
                Role.SYSTEM -> null
            }
        }.joinToString(";")
}