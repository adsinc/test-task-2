package server.data

import java.time.Instant
import java.time.temporal.ChronoUnit.MINUTES

import server.data.Data.Candlesticks.Candlestick
import server.data.Data.Transactions.Transaction
import server.data.Data.{Candlesticks, _}

import scala.collection.immutable.TreeMap
import scala.math.Ordering.Implicits._

class CandlestickStorage private(val storage: TreeMap[Instant, TreeMap[Ticker, Candlestick]], historyLen: Int) {
  def updateFrom(msg: Transaction): CandlestickStorage = {
    val trunkedTs = msg.timestamp.truncatedTo(MINUTES)
    val tickerCandlesticks = storage.getOrElse(trunkedTs, TreeMap.empty[Ticker, Candlestick])
    val updatedCandleStick = tickerCandlesticks.get(msg.ticker)
      .map(Candlesticks.update(_, msg))
      .getOrElse(Candlesticks.create(trunkedTs, msg))
    val updatedStorage = storage + (trunkedTs -> (tickerCandlesticks + (msg.ticker -> updatedCandleStick)))
    new CandlestickStorage(updatedStorage, historyLen)
  }

  def tryRemoveOldCandlesticks(implicit currentTime: Instant): CandlestickStorage = {
    val minTimeStamp = minStoredTimestamp(currentTime)
    new CandlestickStorage(storage.dropWhile(_._1 < minTimeStamp), historyLen)
  }

  private def minStoredTimestamp(currentTime: Instant): Instant =
    currentTime.truncatedTo(MINUTES).minus(historyLen, MINUTES)

  def actualCandlesticks(implicit currentTime: Instant): Iterable[Candlestick] = {
    val currentMinute = currentTime.truncatedTo(MINUTES)
    for {
      allMinutesCandles <- storage.takeWhile(_._1 < currentMinute).values
      candles <- allMinutesCandles.values
    } yield candles
  }

  def prevMinuteCandlesticks(implicit currentTime: Instant): Iterable[Candlestick] =
    storage
      .get(currentTime.truncatedTo(MINUTES).minus(1, MINUTES))
      .map(_.values)
      .getOrElse(Iterable.empty)

}

object CandlestickStorage {
  def apply(historyLen: Int) = new CandlestickStorage(TreeMap.empty, historyLen)
}