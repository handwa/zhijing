package com.example.zhijing

class LlmEngine{

    companion object{
        init{
            System.loadLibrary("zhijing_llm")
        }
    }
    private external fun nativeLoadModel(modelPath: String):Long
    private external fun nativeGenerate(
        modelHandle:Long,
        prompt:String,
        maxTokens:Int
    ):String
    private external fun nativeFreeModel(modelHandle:Long)
    private var modelHandle: Long=0
    fun loadModel(path:String){
        modelHandle=nativeLoadModel(path)
        check(modelHandle!=0L){
            "模型加载失败"
        }
    }
    fun generate(prompt:String):String{
        check(modelHandle!=0L){
            "模型还没有加载"
        }
        return nativeGenerate(
            modelHandle=modelHandle,
            prompt=prompt,
            maxTokens=256
        )
    }
    fun close(){
        if(modelHandle!=0L){
            nativeFreeModel(modelHandle)
            modelHandle=0
        }
    }


}

