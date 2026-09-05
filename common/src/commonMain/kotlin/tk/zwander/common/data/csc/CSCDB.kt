package tk.zwander.common.data.csc

import de.halfbit.csv.CsvWithHeader
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tk.zwander.common.generated.resources.Res
import tk.zwander.common.util.I18n.getCountryNameForCode
import tk.zwander.common.util.globalHttpClient

@OptIn(DelicateCoroutinesApi::class)
@Suppress("MemberVisibilityCanBePrivate", "unused")
data object CSCDB {
    const val LIVE_ENDPOINT =
        "https://raw.githubusercontent.com/zacharee/SamloaderKotlin/master/common/src/commonMain/composeResources/files/cscs.csv"

    // Most of these are from:
    // https://tsar3000.com/list-of-samsung-csc-codes-samsung-firmware-csc-codes/
    private val _items = MutableStateFlow<Set<CSCItem>>(setOf())
    val items = _items.asStateFlow()

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
                println("Failed to fetch remote CSC list, using local resource instead. ${e.message}")
            }
        }
    }

    fun getAll(): List<CSCItem> {
        return _items.value.toList()
    }

    fun findForCountryQuery(query: String): List<CSCItem> {
        if (query.isBlank()) return _items.value.toList()

        return _items.value.filter { item ->
            item.countries.any {
                getCountryName(it).contains(query, true)
            }
        }
    }

    fun findForCscQuery(query: String): List<CSCItem> {
        if (query.isBlank()) return _items.value.toList()

        return _items.value.filter { it.code.contains(query, true) }
    }

    fun findForCarrierQuery(query: String): List<CSCItem> {
        if (query.isBlank()) return _items.value.toList()

        return _items.value.filter { item ->
            item.carriers != null
                    && item.carriers.any { it.contains(query, true) }
        }
    }

    fun findForGeneralQuery(query: String, items: Set<CSCItem> = this._items.value): List<CSCItem> {
        if (query.isBlank()) return items.toList()

        return items.filter { item ->
            item.countries.any { getCountryName(it).contains(query, true) }
                    || item.code.contains(query, true)
                    || item.carriers?.any { it.contains(query, true) } == true
        }
    }

    fun getCountryName(code: String): String {
        return try {
            getCountryNameForCode(code) ?: code
        } catch (e: Exception) {
            code
        }
    }

    private suspend fun loadLocalCsv() {
        val cscString = Res.readBytes("files/cscs.csv").decodeToString()

        loadCsv(cscString)
    }

    private fun loadCsv(csvString: String) {
        val csv = CsvWithHeader.fromCsvText(csvString)

        csv?.allRows?.forEach { line ->
            val (code, countries, carriers) = if (line.size == 2) {
                line + null
            } else {
                line
            }

            if (code != null && countries != null) {
                _items.value = (_items.value + CSCItem(
                    code = code,
                    countries = countries.split(";"),
                    carriers = carriers?.split(";"),
                )).toSet()
            }
        }

        _items.value = _items.value.sorted().toSet()
    }

    sealed class SortBy(val ascending: Boolean) {
        abstract fun sortKey(item: CSCItem): String

        class Code(ascending: Boolean) : SortBy(ascending) {
            override fun sortKey(item: CSCItem): String {
                return item.code
            }
        }

        class Country(ascending: Boolean) : SortBy(ascending) {
            override fun sortKey(item: CSCItem): String {
                return item.countries.map { getCountryName(it) }.run {
                    if (ascending) {
                        minOf { it.lowercase() }
                    } else {
                        maxOf { it.lowercase() }
                    }
                }
            }
        }

        class Carrier(ascending: Boolean) : SortBy(ascending) {
            override fun sortKey(item: CSCItem): String {
                return item.carriers?.run {
                    if (ascending) {
                        minOf { it }
                    } else {
                        maxOf { it }
                    }
                } ?: ""
            }
        }
    }
}
