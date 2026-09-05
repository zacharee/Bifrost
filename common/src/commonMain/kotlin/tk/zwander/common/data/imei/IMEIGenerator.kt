@file:OptIn(DelicateCoroutinesApi::class)

package tk.zwander.common.data.imei

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import de.halfbit.csv.CsvWithHeader
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.util.globalHttpClient

data object IMEIGenerator {
    fun makeImeisForTacs(
        tacs: Set<String>,
    ): List<String> {
        return IMEIDatabase.DUMMY_SERIALS.flatMap { serial ->
            tacs.map { tac ->
                val baseImei = "${tac}${serial}"
                calculateCheckDigitForPartialImei(baseImei)
            }
        }
    }

    @Composable
    fun CollectImeisForModel(
        model: String?,
        initialValue: String,
        onValueChange: (List<String>) -> Unit,
    ) {
        val initialModel = remember {
            model
        }
        val initialValue = remember(model) {
            initialValue
        }
        val tacs by remember(model) {
            IMEIDatabase.mapByModel(model)
        }.collectAsState(null)

        LaunchedEffect(model, tacs) {
            if (initialValue.isNotBlank() && model == initialModel) return@LaunchedEffect

            val fullImeis = tacs?.takeIf { it.isNotEmpty() }?.let { tacs ->
                makeImeisForTacs(tacs)
            } ?: listOf()

            onValueChange(fullImeis)
        }
    }

    private fun calculateCheckDigitForFullImei(imei: String): Int {
        val length = imei.length
        val parity = length % 2

        var sum = 0

        for (i in length - 1 downTo 0) {
            var d = imei[i].digitToInt()

            if (i % 2 == parity) {
                d *= 2
            }

            if (d > 9) {
                d -= 9
            }

            sum += d
        }

        return sum % 10
    }

    private fun calculateCheckDigitForPartialImei(baseImei: String): String {
        val check = calculateCheckDigitForFullImei("${baseImei}0")

        val adjustedCheck = if (check == 0) 0 else 10 - check

        return "${baseImei}${adjustedCheck}"
    }
}

data object IMEIDatabase {
    val DUMMY_SERIALS = arrayOf(
        "123456",
        "012345",
        "011111",
        "005555",
        "020202",
    )
    private const val LIVE_ENDPOINT =
        "https://raw.githubusercontent.com/zacharee/SamloaderKotlin/master/common/src/commonMain/moko-resources/files/tacs.csv"

    val tacs = MutableStateFlow<Map<String, Set<String>>>(mapOf())

    init {
        GlobalScope.launch(Dispatchers.IO) {
            loadLocalCsv()
            try {
                val response = globalHttpClient.get(LIVE_ENDPOINT)
                val liveData = response.bodyAsText()

                if (!response.status.isSuccess()) {
                    error(response.status)
                }

                if (liveData.isNotBlank()) {
                    loadCsv(liveData)
                }
            } catch (e: Throwable) {
                println("Failed to fetch remote TAC list, using local resource instead. ${e.message}")
            }
        }
    }

    fun mapByModel(model: String?) = tacs.map { it[model] }

    private fun String.cleanUpModel(): String {
        val trimmed = trim()

        if (!trimmed.contains("-")) {
            return trimmed
        }

        val firstSpace = trimmed.indexOfFirst { it == ' ' }.takeIf { it != -1 } ?: trimmed.length
        val beforeSpaces = trimmed.slice(0 until firstSpace)

        val firstSlash =
            beforeSpaces.indexOfFirst { it == '/' }.takeIf { it != -1 } ?: beforeSpaces.length

        return beforeSpaces.slice(0 until firstSlash)
    }

    private suspend fun loadLocalCsv() {
        loadCsv(Res.readBytes("files/tacs.csv").decodeToString())
    }

    private fun loadCsv(csvString: String) {
        val csv = CsvWithHeader.fromCsvText(csvString)
        csv?.allRows?.forEach { line ->
            if (line.size >= 2) {
                val tac = line[0]
                val model = line[1].cleanUpModel()

                val newMap = tacs.value.toMutableMap()

                val model1Set = (newMap[model] ?: setOf()).toMutableSet()
                model1Set.add(tac)
                newMap[model] = model1Set

                if (line.size > 2) {
                    line.slice(2..line.lastIndex).forEach { secondaryModel ->
                        val cleaned = secondaryModel.cleanUpModel()
                        val secondarySet = (newMap[cleaned] ?: setOf()).toMutableSet()
                        secondarySet.add(tac)
                        newMap[cleaned] = secondarySet
                    }
                }

                tacs.value = newMap
            }
        }
    }
}
