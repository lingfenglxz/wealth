package com.demo.wealth.domain.sports

import com.demo.wealth.data.FootballMatchEntity
import com.demo.wealth.data.FootballRecommendationEntity
import org.json.JSONArray
import org.json.JSONObject

object FootballPlayTypes {
    val allCodes = listOf("had", "hhad", "crs", "ttg", "hafu")

    fun displayName(code: String): String = when (code) {
        "had" -> "胜平负"
        "hhad" -> "让球胜平负"
        "crs" -> "比分"
        "ttg" -> "总进球"
        "hafu" -> "半全场"
        else -> code
    }

    fun selectionName(playType: String, selection: String): String = when (playType) {
        "had", "hhad" -> when (selection) {
            "H" -> "主胜"
            "D" -> "平"
            "A" -> "客胜"
            else -> selection
        }
        "hafu" -> selection.chunked(1).joinToString("/") {
            when (it) {
                "H" -> "胜"
                "D" -> "平"
                "A" -> "负"
                else -> it
            }
        }
        else -> selection
    }
}

object FootballDisplayNames {
    private val teams = mapOf(
        "Mexico" to "墨西哥",
        "South Africa" to "南非",
        "Korea Republic" to "韩国",
        "Czechia" to "捷克",
        "Canada" to "加拿大",
        "Bosnia and Herzegovina" to "波黑",
        "USA" to "美国",
        "Paraguay" to "巴拉圭",
        "Haiti" to "海地",
        "Scotland" to "苏格兰",
        "Australia" to "澳大利亚",
        "Turkiye" to "土耳其",
        "Brazil" to "巴西",
        "Morocco" to "摩洛哥",
        "Qatar" to "卡塔尔",
        "Switzerland" to "瑞士",
        "Cote d'Ivoire" to "科特迪瓦",
        "Ecuador" to "厄瓜多尔",
        "Germany" to "德国",
        "Curacao" to "库拉索",
        "Netherlands" to "荷兰",
        "Japan" to "日本",
        "Sweden" to "瑞典",
        "Tunisia" to "突尼斯",
        "Saudi Arabia" to "沙特阿拉伯",
        "Uruguay" to "乌拉圭",
        "Spain" to "西班牙",
        "Cabo Verde" to "佛得角",
        "Iran" to "伊朗",
        "New Zealand" to "新西兰",
        "Belgium" to "比利时",
        "Egypt" to "埃及",
        "France" to "法国",
        "Senegal" to "塞内加尔",
        "Iraq" to "伊拉克",
        "Norway" to "挪威",
        "Argentina" to "阿根廷",
        "Algeria" to "阿尔及利亚",
        "Austria" to "奥地利",
        "Jordan" to "约旦",
        "Ghana" to "加纳",
        "Panama" to "巴拿马",
        "England" to "英格兰",
        "Croatia" to "克罗地亚",
        "Portugal" to "葡萄牙",
        "DR Congo" to "刚果民主共和国",
        "Uzbekistan" to "乌兹别克斯坦",
        "Colombia" to "哥伦比亚"
    )

    private val venues = mapOf(
        "FIFA World Cup 2026" to "2026 世界杯",
        "Mexico City Stadium" to "墨西哥城球场",
        "Estadio Guadalajara" to "瓜达拉哈拉球场",
        "Toronto Stadium" to "多伦多球场",
        "Los Angeles Stadium" to "洛杉矶球场",
        "Boston Stadium" to "波士顿球场",
        "BC Place Vancouver" to "温哥华 BC Place",
        "New York New Jersey Stadium" to "纽约新泽西球场",
        "San Francisco Bay Area Stadium" to "旧金山湾区球场",
        "Philadelphia Stadium" to "费城球场",
        "Houston Stadium" to "休斯敦球场",
        "Dallas Stadium" to "达拉斯球场",
        "Estadio Monterrey" to "蒙特雷球场",
        "Miami Stadium" to "迈阿密球场",
        "Atlanta Stadium" to "亚特兰大球场",
        "Seattle Stadium" to "西雅图球场",
        "Kansas City Stadium" to "堪萨斯城球场",
        "Mexico City" to "墨西哥城",
        "Guadalajara" to "瓜达拉哈拉",
        "Toronto" to "多伦多",
        "Los Angeles" to "洛杉矶",
        "Boston" to "波士顿",
        "Vancouver" to "温哥华",
        "New York / New Jersey" to "纽约/新泽西",
        "San Francisco Bay Area" to "旧金山湾区",
        "Philadelphia" to "费城",
        "Houston" to "休斯敦",
        "Dallas" to "达拉斯",
        "Monterrey" to "蒙特雷",
        "Miami" to "迈阿密",
        "Atlanta" to "亚特兰大",
        "Seattle" to "西雅图",
        "Kansas City" to "堪萨斯城"
    )

    fun team(name: String): String = teams[name] ?: name

    fun venue(name: String): String = venues[name] ?: name

    fun phase(value: String): String {
        var text = value
            .replace("Group ", "小组赛 ")
            .replace("Round of 32", "32 强")
            .replace("Round of 16", "16 强")
            .replace("Quarter-final", "四分之一决赛")
            .replace("Semi-final", "半决赛")
            .replace("Third-place", "三四名决赛")
            .replace("Final", "决赛")
        return text
    }

    fun reason(text: String): String {
        var result = text
            .replace("Poisson", "泊松模型")
            .replace("edge", "理论价值")
        teams.forEach { (english, chinese) -> result = result.replace(english, chinese) }
        return result
    }
}

object FootballLotteryParser {
    fun parseMatches(text: String): List<FootballMatchEntity> {
        val root = JSONObject(text)
        val matches = root.optJSONArray("matches") ?: return emptyList()
        return (0 until matches.length()).mapNotNull { index ->
            val item = matches.optJSONObject(index) ?: return@mapNotNull null
            val matchId = item.optString("matchId")
            val home = item.optString("homeTeam")
            val away = item.optString("awayTeam")
            val kickoff = item.optString("kickoffTime")
            val pools = item.optJSONObject("pools") ?: JSONObject()
            if (matchId.isBlank() || home.isBlank() || away.isBlank() || kickoff.isBlank()) {
                return@mapNotNull null
            }
            FootballMatchEntity(
                matchId = matchId,
                matchNum = item.optString("matchNum", matchId),
                leagueName = FootballDisplayNames.venue(item.optString("leagueName", "FIFA World Cup 2026")),
                phase = FootballDisplayNames.phase(item.optString("phase", "世界杯")),
                kickoffTime = kickoff,
                homeTeam = FootballDisplayNames.team(home),
                awayTeam = FootballDisplayNames.team(away),
                handicap = item.optInt("handicap"),
                poolsJson = pools.toString(),
                stadium = FootballDisplayNames.venue(item.optString("stadium")),
                city = FootballDisplayNames.venue(item.optString("city")),
                source = item.optString("source", root.optString("sourceStatus", "fallback")),
                updatedAt = item.optString("updatedAt")
            )
        }
    }

    fun parseRecommendations(text: String): List<FootballRecommendationEntity> {
        val root = JSONObject(text)
        val items = root.optJSONArray("recommendations") ?: return emptyList()
        val createdAt = System.currentTimeMillis()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val matchId = item.optString("matchId")
            val selection = item.optString("selection")
            if (matchId.isBlank() || selection.isBlank()) return@mapNotNull null
            FootballRecommendationEntity(
                createdAt = createdAt,
                matchId = matchId,
                matchNum = item.optString("matchNum", matchId),
                leagueName = FootballDisplayNames.venue(item.optString("leagueName", "FIFA World Cup 2026")),
                phase = FootballDisplayNames.phase(item.optString("phase", "世界杯")),
                kickoffTime = item.optString("kickoffTime"),
                homeTeam = FootballDisplayNames.team(item.optString("homeTeam")),
                awayTeam = FootballDisplayNames.team(item.optString("awayTeam")),
                playType = item.optString("playType"),
                playName = item.optString("playName", FootballPlayTypes.displayName(item.optString("playType"))),
                modelName = item.optString("modelName"),
                selection = selection,
                odds = item.optDouble("odds", 0.0),
                confidence = item.optDouble("confidence", 0.0),
                fairProbability = item.optDouble("fairProbability", 0.0),
                modelProbability = item.optDouble("modelProbability", 0.0),
                edge = item.optDouble("edge", 0.0),
                dataQuality = item.optDouble("dataQuality", 0.0),
                homeExpectedGoals = item.optJSONObject("expectedGoals")?.optDouble("home", 0.0) ?: 0.0,
                awayExpectedGoals = item.optJSONObject("expectedGoals")?.optDouble("away", 0.0) ?: 0.0,
                reasonsJson = item.optJSONArray("reasons")?.let { reasons ->
                    JSONArray().also { translated ->
                        for (reasonIndex in 0 until reasons.length()) {
                            translated.put(FootballDisplayNames.reason(reasons.optString(reasonIndex)))
                        }
                    }.toString()
                } ?: JSONArray().toString()
            )
        }
    }
}
