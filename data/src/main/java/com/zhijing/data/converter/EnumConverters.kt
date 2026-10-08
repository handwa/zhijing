package com.zhijing.data.converter

import androidx.room.TypeConverter
import com.zhijing.data.model.AnswerStatus
import com.zhijing.data.model.AnalysisStatus
import com.zhijing.data.model.AssociationFeedback
import com.zhijing.data.model.EmbeddingStatus
import com.zhijing.data.model.ImportChannel
import com.zhijing.data.model.ParseTaskStatus
import com.zhijing.data.model.ParseTaskType
import com.zhijing.data.model.PetPhase
import com.zhijing.data.model.StanceStatus
import com.zhijing.data.model.VectorOwnerType
import com.zhijing.data.model.WakeMode

/**
 * 枚举 ⇄ 数据库表示的类型转换器。
 *
 * 说明书 3.4 明确这些字段在库内是 INTEGER（状态类）或 TEXT（类型类），
 * 因此这里**不使用** Room 默认的「枚举名存成 TEXT」行为，全部显式转换。
 */
internal class EnumConverters {

    /* ---------------------------------------------------------- INTEGER 状态 */

    @TypeConverter
    fun analysisStatusToCode(value: AnalysisStatus): Int = value.code

    @TypeConverter
    fun codeToAnalysisStatus(code: Int): AnalysisStatus = AnalysisStatus.fromCode(code)

    @TypeConverter
    fun embeddingStatusToCode(value: EmbeddingStatus): Int = value.code

    @TypeConverter
    fun codeToEmbeddingStatus(code: Int): EmbeddingStatus = EmbeddingStatus.fromCode(code)

    @TypeConverter
    fun associationFeedbackToCode(value: AssociationFeedback): Int = value.code

    @TypeConverter
    fun codeToAssociationFeedback(code: Int): AssociationFeedback = AssociationFeedback.fromCode(code)

    @TypeConverter
    fun stanceStatusToCode(value: StanceStatus): Int = value.code

    @TypeConverter
    fun codeToStanceStatus(code: Int): StanceStatus = StanceStatus.fromCode(code)

    @TypeConverter
    fun answerStatusToCode(value: AnswerStatus): Int = value.code

    @TypeConverter
    fun codeToAnswerStatus(code: Int): AnswerStatus = AnswerStatus.fromCode(code)

    @TypeConverter
    fun petPhaseToCode(value: PetPhase): Int = value.code

    @TypeConverter
    fun codeToPetPhase(code: Int): PetPhase = PetPhase.fromCode(code)

    @TypeConverter
    fun parseTaskStatusToCode(value: ParseTaskStatus): Int = value.code

    @TypeConverter
    fun codeToParseTaskStatus(code: Int): ParseTaskStatus = ParseTaskStatus.fromCode(code)

    /* ------------------------------------------------------------- TEXT 类型 */

    @TypeConverter
    fun importChannelToWire(value: ImportChannel): String = value.wire

    @TypeConverter
    fun wireToImportChannel(wire: String?): ImportChannel = ImportChannel.fromWire(wire)

    @TypeConverter
    fun wakeModeToWire(value: WakeMode): String = value.wire

    @TypeConverter
    fun wireToWakeMode(wire: String?): WakeMode = WakeMode.fromWire(wire)

    @TypeConverter
    fun parseTaskTypeToWire(value: ParseTaskType): String = value.wire

    @TypeConverter
    fun wireToParseTaskType(wire: String?): ParseTaskType = ParseTaskType.fromWire(wire)

    @TypeConverter
    fun vectorOwnerTypeToWire(value: VectorOwnerType): String = value.wire

    @TypeConverter
    fun wireToVectorOwnerType(wire: String?): VectorOwnerType = VectorOwnerType.fromWire(wire)
}
