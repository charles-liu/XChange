package org.knowm.xchange.okcoin;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.FundingRecord;
import org.knowm.xchange.dto.account.FundingRecord.Status;
import org.knowm.xchange.dto.account.FundingRecord.Type;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.okcoin.v3.dto.account.OkexDepositRecord;
import org.knowm.xchange.okcoin.v3.dto.account.OkexFundingAccountRecord;
import org.knowm.xchange.okcoin.v3.dto.account.OkexSpotAccountRecord;
import org.knowm.xchange.okcoin.v3.dto.account.OkexWithdrawalRecord;
import org.knowm.xchange.okcoin.v3.dto.marketdata.OkexSpotTicker;
import org.knowm.xchange.okcoin.v3.dto.trade.FuturesAccountsResponse.FuturesAccount;
import org.knowm.xchange.okcoin.v3.dto.trade.OkexOpenOrder;
import org.knowm.xchange.okcoin.v3.dto.trade.Side;
import org.knowm.xchange.okcoin.v3.dto.trade.SwapAccountsResponse.SwapAccountInfo;

public class OkexAdaptersV3 {

  public static Balance convert(OkexFundingAccountRecord rec) {
    return new Balance.Builder()
        .currency(Currency.getInstance(rec.getCurrency()))
        .available(rec.getAvailable())
        .frozen(rec.getBalance().subtract(rec.getAvailable()))
        .total(rec.getBalance())
        .build();
  }

  public static Balance convert(OkexSpotAccountRecord rec) {
    return new Balance.Builder()
        .currency(Currency.getInstance(rec.getCurrency()))
        .available(rec.getAvailable())
        .frozen(rec.getBalance().subtract(rec.getAvailable()))
        .total(rec.getBalance())
        .build();
  }

  public static Balance convert(String currency, FuturesAccount acc) {
    return new Balance.Builder()
        .currency(Currency.getInstance(currency.toUpperCase()))
        .total(acc.getEquity())
        .build();
  }

  public static Balance convert(SwapAccountInfo rec) {
    return new Balance.Builder()
        .currency(toPair(rec.getInstrumentId()).base)
        .total(rec.getEquity())
        .build();
  }

  public static String toSpotInstrument(CurrencyPair pair) {
    return pair == null ? null : pair.base.getCurrencyCode() + "-" + pair.counter.getCurrencyCode();
  }

  /**
   * there are different types of instruments: spot (ie 'ETH-BTC'), future (ie 'BCH-USD-190927'),
   * swap (ie 'ETH-USD-SWAP')
   *
   * @param instrument
   * @return
   */
  public static CurrencyPair toPair(String instrument) {
    String[] split = instrument.split("-");
    if (split == null || split.length < 2) {
      throw new ExchangeException("Not supported instrument: " + instrument);
    }
    return new CurrencyPair(split[0], split[1]);
  }

  public static Ticker convert(OkexSpotTicker i) {
    return new Ticker.Builder()
        .currencyPair(toPair(i.getInstrumentId()))
        .last(i.getLast())
        .bid(i.getBestBid())
        .bidSize(i.getBestBidSize())
        .ask(i.getBestAsk())
        .askSize(i.getBestAskSize())
        .open(i.getOpen24h())
        .high(i.getHigh24h())
        .low(i.getLow24h())
        .volume(i.getBaseVolume24h())
        .quoteVolume(i.getQuoteVolume24h())
        .timestamp(i.getTimestamp())
        .build();
  }

  public static LimitOrder convert(OkexOpenOrder o) {
    return new LimitOrder.Builder(
            o.getSide() == Side.sell ? OrderType.ASK : OrderType.BID, toPair(o.getInstrumentId()))
        .id(o.getOrderId())
        .limitPrice(o.getPrice())
        .originalAmount(o.getSize())
        .cumulativeAmount(o.getFilledSize())
        .timestamp(o.getCreatedAt())
        .orderStatus(convertOrderStatus(o.getState()))
        .build();
  }

  public static FundingRecord adaptFundingRecord(OkexWithdrawalRecord r) {
    return new FundingRecord.Builder()
        .setAddress(r.getTo())
        .setAmount(r.getAmount())
        .setCurrency(Currency.getInstance(r.getCurrency()))
        .setDate(r.getTimestamp())
        .setInternalId(r.getWithdrawalId())
        .setStatus(convertWithdrawalStatus(r.getStatus()))
        .setBlockchainTransactionHash(r.getTxid())
        .setType(Type.WITHDRAWAL)
        .build();
  }

  /**
   * @param okexOrderState -2:失败 -1:撤单成功 0:等待成交 1:部分成交 2:完全成交 3:下单中 4:撤单中
   * @return
   */
  private static Order.OrderStatus convertOrderStatus(String okexOrderState) {
    switch (okexOrderState) {
      case "-1":
        return Order.OrderStatus.CANCELED;
      case "0":
        return Order.OrderStatus.NEW;
      case "1":
        return Order.OrderStatus.PARTIALLY_FILLED;
      case "2":
        return Order.OrderStatus.FILLED;
      case "3":
        return Order.OrderStatus.PENDING_NEW;
      case "4":
        return Order.OrderStatus.PENDING_CANCEL;
      case "-2":
        return Order.OrderStatus.REJECTED;
      default:
        return Order.OrderStatus.UNKNOWN;
    }
  }
  /**
   * -3:pending cancel; -2: cancelled; -1: failed; 0 :pending; 1 :sending; 2:sent; 3 :email
   * confirmation; 4 :manual confirmation; 5:awaiting identity confirmation
   */
  private static Status convertWithdrawalStatus(String status) {
    switch (status) {
      case "-3":
      case "-2":
        return Status.CANCELLED;
      case "-1":
        return Status.FAILED;
      case "0":
      case "1":
      case "3":
      case "4":
      case "5":
        return Status.PROCESSING;
      case "2":
        return Status.COMPLETE;
      default:
        throw new ExchangeException("Unknown withdrawal status: " + status);
    }
  }

  public static FundingRecord adaptFundingRecord(OkexDepositRecord r) {
    return new FundingRecord.Builder()
        .setAddress(r.getTo())
        .setAmount(r.getAmount())
        .setCurrency(Currency.getInstance(r.getCurrency()))
        .setDate(r.getTimestamp())
        .setStatus(convertDepositStatus(r.getStatus()))
        .setBlockchainTransactionHash(r.getTxid())
        .setType(Type.DEPOSIT)
        .build();
  }
  /**
   * The status of deposits (0: waiting for confirmation; 1: confirmation account; 2: recharge
   * success);
   */
  private static Status convertDepositStatus(String status) {
    switch (status) {
      case "0":
      case "1":
      case "6":
        return Status.PROCESSING;
      case "2":
        return Status.COMPLETE;
      default:
        throw new ExchangeException("Unknown deposit status: " + status);
    }
  }
}
