package net.svalukhin.holdem.dojo.client

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.Math.abs
import java.time.LocalDateTime


/**
 * @author Yevgeniy Svalukhin
 */

enum class CardSuit(val figure: String) {
    SPADES("♠"), HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣"), NONE("");

    override fun toString(): String = figure
}

fun String.cardSuit(): CardSuit = when (this) {
    CardSuit.CLUBS.figure -> CardSuit.CLUBS
    CardSuit.HEARTS.figure -> CardSuit.HEARTS
    CardSuit.SPADES.figure -> CardSuit.SPADES
    CardSuit.DIAMONDS.figure -> CardSuit.DIAMONDS
    else -> CardSuit.NONE
}

enum class CardValue(val value: String) : Comparable<CardValue> {
    TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"), EIGHT("8"), NINE("9"), TEN("10"), JACK("V"), QUEEN("Q"), KING("K"), ACE("A");

    override fun toString(): String = value
}

fun String.cardValue(): CardValue = when (this) {
    CardValue.TWO.value -> CardValue.TWO
    CardValue.THREE.value -> CardValue.THREE
    CardValue.FOUR.value -> CardValue.FOUR
    CardValue.FIVE.value -> CardValue.FIVE
    CardValue.SIX.value -> CardValue.SIX
    CardValue.SEVEN.value -> CardValue.SEVEN
    CardValue.EIGHT.value -> CardValue.EIGHT
    CardValue.NINE.value -> CardValue.NINE
    CardValue.TEN.value -> CardValue.TEN
    CardValue.JACK.value -> CardValue.JACK
    CardValue.QUEEN.value -> CardValue.QUEEN
    CardValue.KING.value -> CardValue.KING
    CardValue.ACE.value -> CardValue.ACE
    else -> throw IllegalStateException()
}

data class Card(private val cardValue: String, private val cardSuit: String) : Comparable<Card> {
    private var _suit: CardSuit? = null
    private var _value: CardValue? = null

    val suit: CardSuit
        get() {
            if (_suit == null) {
                _suit = cardSuit.cardSuit()
            }
            return _suit ?: throw IllegalStateException()
        }
    val value: CardValue
        get() {
            if (_value == null) {
                _value = cardValue.cardValue()
            }
            return _value ?: throw IllegalStateException()
        }

    fun isSameSuit(card: Card): Boolean = card.suit == suit

    override fun toString(): String = cardValue.plus(cardSuit)

    override fun compareTo(other: Card): Int = suit.ordinal.compareTo(other.suit.ordinal)

}

data class Player(val name: String, val balance: Int, val pot: Int, val status: String, val cards: List<Card>) {

    private var command: Action? = null

    val action: Action
        get() {
            if (command == null) {
                command = status.action()
            }
            return command ?: throw IllegalStateException()
        }

    override fun toString(): String {
        return "Player".plus("(")
                .plus("name=").plus(name).plus(", ")
                .plus("balance=").plus(balance).plus(", ")
                .plus("pot=").plus(pot).plus(", ")
                .plus("status=").plus(status).plus(", ")
                .plus("action=").plus(action).plus(", ")
                .plus("cards=").plus(cards).plus(")")
    }
}

enum class Round {
    BLIND, THREE_CARDS, FOUR_CARDS, FIVE_CARDS, FINAL
}

enum class Combination(val comment: String) {
    HIGH_CARD("Highest card"), ONE_PAIR("Two cards of same suit"), TWO_PAIR("Two sets of the same rank"),
    THREE_OF_A_KIND("Three cards of same suit"), STRAIGHT("Five cards in sequence"), FLUSH("Five cards all of the same suit"),
    FULL_HOUSE("Threw cards of one rank and two cards of another rank"), FOUR_OF_A_KIND("Four cards of the same rank"),
    STRAIGHT_FLUSH("Highest hand that consists of any other straight of the same suit"), ROYAL_FLUSH("Highest hand that consists of Ten, Jack, Queen, King and Ace");

}

data class Event(val gameRound: Round,
                 val dealer: String,
                 val mover: String,
                 val event: List<String>,
                 val players: List<Player>,
                 val combination: String,
                 val gameStatus: String,
                 val deskCards: List<Card>,
                 val deskPot: Int)

enum class Action(val action: String) {
    FOLD("Fold"), CHECK("Check"), CALL("Call"), RISE("Rise"), ALL_IN("AllIn"), NONE("None"), NOT_MOVED("NotMoved"), SMALL_BLIND("SmallBLind"), BIG_BLIND("BigBlind");

    override fun toString(): String = action

}

fun String.action(): Action = when (this) {
    "Fold" -> Action.FOLD
    "Check" -> Action.CHECK
    "Call" -> Action.CALL
    "Rise" -> Action.RISE
    "AllIn" -> Action.ALL_IN
    "NotMoved" -> Action.NOT_MOVED
    "SmallBLind" -> Action.SMALL_BLIND
    "BigBlind" -> Action.BIG_BLIND
    else -> Action.NONE
}

interface Strategy {
    fun action(raise: Int): String
}

class FoldStrategy : Strategy {
    override fun action(raise: Int): String = Action.FOLD.toString()
}

class CheckStrategy : Strategy {
    override fun action(raise: Int): String = Action.CHECK.toString()
}

class CallStrategy : Strategy {
    override fun action(raise: Int): String {
        return Action.CALL.toString()
    }
}

class RiseStrategy : Strategy {
    override fun action(raise: Int): String = Action.RISE.toString().plus(",").plus(raise)
}

class AllInStrategy : Strategy {
    override fun action(raise: Int): String = Action.ALL_IN.toString()
}

class DoNothingStrategy : Strategy {
    override fun action(raise: Int): String = ""
}


interface Analyzer {
    fun resolveStrategy(event: Event): Pair<Strategy?, Int>
}

interface CombinationCheck {
    fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?>
}

class OnePairCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.value.value }
//        println(groupBy)
        val filter = groupBy.filter { it.value.size >= 2 }
//        println(filter)
        return if (filter.isEmpty()) Pair(false, emptyList()) else Pair(true, filter[filter.keys.first()])
    }
}

class TwoPairCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.value.value }
//        println(groupBy)
        val filter = groupBy.filter { it.value.size >= 2 }
//        println(filter)
        return if (filter.isEmpty()) Pair(false, emptyList()) else Pair(true, filter.flatMap { it.value })
    }
}

class ThreeOfaKindCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.value.value }
//        println(groupBy)
        val filter = groupBy.filter { it.value.size >= 3 }
//        println(filter)
        return if (filter.isEmpty()) Pair(false, emptyList()) else Pair(true, filter[filter.keys.first()])
    }
}

class StraightCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val sorted = cards.sorted()
//        println(sorted)
        val zip = sorted.zip(sorted.drop(1))
//        println(zip)
        return Pair(zip.all { abs(it.first.value.compareTo(it.second.value)) == 1 }, sorted)
    }
}

class FlushCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.suit.figure }
//        println (groupBy)
        return Pair(groupBy.size == 5, groupBy.getOrDefault(groupBy.keys.first(), emptyList()))
    }
}

class FullHouseCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.value.value }
        val containsPair = groupBy.filter { it.value.size == 2 }
        val containsThreeOfaKind = groupBy.filter { it.value.size == 3 }

        val pairList = if (containsPair.isEmpty()) emptyList() else containsPair[containsPair.keys.first()].orEmpty()
        val threeList = if (containsPair.isEmpty()) emptyList() else containsThreeOfaKind[containsPair.keys.first()].orEmpty()

        return if (containsPair.isNotEmpty() && containsThreeOfaKind.isNotEmpty()) Pair(true, mutableListOf<Card>().plus(pairList).plus(threeList)) else Pair(false, emptyList())

    }
}

class FourOfaKindCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val groupBy = cards.groupBy { it.value.value }
//        println(groupBy)
        val filter = groupBy.filter { it.value.size >= 4 }
//        println(filter)
        return if (filter.isEmpty()) Pair(false, emptyList()) else Pair(true, filter[filter.keys.first()])
    }
}

class StraightFlushCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val sorted = cards.sorted()
//        println(sorted)
        val zip = sorted.zip(sorted.drop(1))
//        println(zip)
        return Pair(zip.all { it.first.suit == it.second.suit && abs(it.first.value.compareTo(it.second.value)) == 1 }, sorted)
    }
}

class RoyalFlushCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> {
        val sorted = cards.sorted()
//        println(sorted)
        val zip = sorted.zip(sorted.drop(1))
//        println(zip)
        return Pair(zip.all { it.first.suit == it.second.suit } && sorted.first().value == CardValue.TEN && sorted.last().value == CardValue.ACE, sorted)
    }
}

class HighCardCombinationCheck : CombinationCheck {
    override fun checkForCombination(cards: List<Card>): Pair<Boolean, List<Card>?> = Pair(true, listOf(cards.sorted().last()))
}


class StrategyAnalyzer(val user: String) : Analyzer {

    val strategies: Map<Action, Strategy> = mapOf(Action.FOLD to FoldStrategy(),
            Action.CHECK to CheckStrategy(),
            Action.CALL to CallStrategy(),
            Action.RISE to RiseStrategy(),
            Action.ALL_IN to AllInStrategy(),
            Action.NONE to DoNothingStrategy())

    val combinationChecks: Map<Combination, CombinationCheck> = mapOf(
            Combination.HIGH_CARD to HighCardCombinationCheck(),
            Combination.ONE_PAIR to OnePairCombinationCheck(),
            Combination.TWO_PAIR to TwoPairCombinationCheck(),
            Combination.THREE_OF_A_KIND to ThreeOfaKindCombinationCheck(),
            Combination.STRAIGHT to StraightCombinationCheck(),
            Combination.FLUSH to FlushCombinationCheck(),
            Combination.FULL_HOUSE to FullHouseCombinationCheck(),
            Combination.FOUR_OF_A_KIND to FourOfaKindCombinationCheck(),
            Combination.STRAIGHT_FLUSH to StraightFlushCombinationCheck(),
            Combination.ROYAL_FLUSH to RoyalFlushCombinationCheck())

    var currentCombination: Combination = Combination.HIGH_CARD
    var lastCombination: Combination = Combination.HIGH_CARD
    var previousRound: Round = Round.FINAL


    override fun resolveStrategy(event: Event): Pair<Strategy?, Int> {

        if (event.event[0] == "BLIND game round started.") {

            lastCombination = Combination.HIGH_CARD
        }

        previousRound = event.gameRound

        if (event.mover != user && !event.event[0].endsWith("moves.")) {
            return Pair(strategies[Action.NONE], 0)
        }


        val myState = event.players.find { it.name == user && !it.cards.isEmpty() } ?: return Pair(strategies[Action.NONE], 0)

        val newCombination = combination(myState.cards, event.deskCards)

        val riseAmount = when {
            myState.balance / 10000 > 1 -> 2500
            myState.balance / 5000 > 1 -> 1250
            myState.balance / 1000 > 1 -> 250
            myState.balance / 500 > 1 -> 125
            myState.balance / 100 > 1 -> 25
            myState.balance / 50 > 1 -> 5
            else -> 0
        }

        val combinationChanged = newCombination != lastCombination

        println(newCombination)

        return when (event.gameRound) {
            Round.BLIND -> {
                if (newCombination == Combination.TWO_PAIR) Pair(strategies[Action.RISE],riseAmount) else Pair(strategies[Action.CALL], 0)
            }
            Round.THREE_CARDS -> {
                when (newCombination) {
                    Combination.ROYAL_FLUSH, Combination.STRAIGHT_FLUSH, Combination.FOUR_OF_A_KIND, Combination.FULL_HOUSE -> {
                        return if (combinationChanged) Pair(strategies[Action.RISE], riseAmount) else Pair(strategies[Action.CHECK],0)
                    }
                    Combination.FLUSH, Combination.STRAIGHT, Combination.THREE_OF_A_KIND -> if (combinationChanged) Pair(strategies[Action.CALL], 0) else Pair(strategies[Action.CHECK],0)
                    Combination.TWO_PAIR, Combination.ONE_PAIR -> Pair(strategies[Action.CHECK], 0)
                    else -> Pair(strategies[Action.CHECK], 0)
                }

            }
            Round.FOUR_CARDS -> {
                when (newCombination) {
                    Combination.ROYAL_FLUSH, Combination.STRAIGHT_FLUSH, Combination.FOUR_OF_A_KIND, Combination.FULL_HOUSE -> {
                        return if (combinationChanged) Pair(strategies[Action.RISE], riseAmount) else Pair(strategies[Action.CHECK],0)
                    }
                    Combination.FLUSH, Combination.STRAIGHT, Combination.THREE_OF_A_KIND -> if (combinationChanged) Pair(strategies[Action.CALL], 0) else Pair(strategies[Action.CHECK],0)
                    Combination.TWO_PAIR, Combination.ONE_PAIR -> Pair(strategies[Action.CHECK], 0)
                    else -> Pair(strategies[Action.FOLD], 0)
                }

            }
            Round.FIVE_CARDS -> {
                when (newCombination) {
                    Combination.ROYAL_FLUSH, Combination.STRAIGHT_FLUSH, Combination.FOUR_OF_A_KIND, Combination.FULL_HOUSE -> {
                        return if (combinationChanged) Pair(strategies[Action.RISE], riseAmount) else Pair(strategies[Action.CHECK],0)
                    }
                    Combination.FLUSH, Combination.STRAIGHT, Combination.THREE_OF_A_KIND -> if (combinationChanged) Pair(strategies[Action.CALL], 0) else Pair(strategies[Action.CHECK],0)
                    Combination.TWO_PAIR, Combination.ONE_PAIR -> Pair(strategies[Action.CHECK], 0)
                    else -> Pair(strategies[Action.FOLD], 0)
                }

            }
            Round.FINAL -> {
                when (newCombination) {
                    Combination.ROYAL_FLUSH, Combination.STRAIGHT_FLUSH, Combination.FOUR_OF_A_KIND, Combination.FULL_HOUSE -> {
                        return Pair(strategies[Action.RISE], riseAmount)
                    }
                    Combination.FLUSH, Combination.STRAIGHT, Combination.THREE_OF_A_KIND -> if (combinationChanged) Pair(strategies[Action.CALL], 0) else Pair(strategies[Action.CHECK],0)
                    Combination.TWO_PAIR, Combination.ONE_PAIR -> Pair(strategies[Action.CHECK], 0)
                    else -> Pair(strategies[Action.FOLD], 0)
                }

            }
        }
    }

    fun combination(handCards: List<Card>, deskCards: List<Card>): Combination {
        val cards = arrayListOf<Card>()
        cards.addAll(handCards)
        cards.addAll(deskCards)

        cards.sort()


        for (i in currentCombination.ordinal..Combination.values().size-1) {

//            println(LocalDateTime.now().toString().plus(" index is ".plus(i)))

            val checkedCombination = Combination.values()[i]

//            println(LocalDateTime.now().toString().plus(" Checking now combination ".plus(checkedCombination)))

//            println(cards)
            val (isWinCombination, combinationCards) = combinationChecks[checkedCombination]!!.checkForCombination(cards)

//            println("Is combination winnable ".plus(isWinCombination))
//            println(combinationCards)

            if (isWinCombination) {
//                println(LocalDateTime.now().toString().plus(" Current combination ".plus(checkedCombination)))
//                println(LocalDateTime.now().toString().plus(" Cards ".plus(combinationCards)))
                currentCombination = checkedCombination
            }
        }

        println(LocalDateTime.now().toString().plus(" Combination ".plus(currentCombination)))

        return currentCombination

    }
}


class HoldemListener(val analyzer: Analyzer) : WebSocketListener() {

    val gson = Gson()

    override fun onMessage(webSocket: WebSocket?, text: String?) {
        val event = gson.fromJson<Event>(text, Event::class.java)
        println("---------------------------------------------------")
        println(LocalDateTime.now())
        println(event)
        println("---------------------------------------------------")

        val (strategy, bet) = analyzer.resolveStrategy(event)

        if (strategy != null) {
            webSocket?.send(strategy.action(bet))
        }
    }

    override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
        super.onClosing(webSocket, code, reason)
        webSocket?.close(code, reason)
    }


    override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
        webSocket?.close(code, reason)
    }

}

data class Option(val flag: String, val value: String)

fun main(args: Array<String>) {

//    val options = ArrayList<Option>()
//
//    (0..(args.size / 2)).mapTo(options) { Option(args[it], args[it +1]) }
//
//    val host = options.find { "-host" == it.flag }?.value
//
//    val user = options.find { "-user" == it.flag }?.value
//
//    val password = options.find { ""}


    val user = "eugene-bot"

    val password = "pass"
    val host = "10.14.160.149"
//    val host = "localhost"

    val connectionString = "ws://".plus(host).plus(":8080/ws?user=").plus(user).plus("&password=").plus(password)

    val client = OkHttpClient()

    val request = Request.Builder().url(connectionString).build()

    val newWebSocket = client.newWebSocket(request, HoldemListener(StrategyAnalyzer(user)))


}