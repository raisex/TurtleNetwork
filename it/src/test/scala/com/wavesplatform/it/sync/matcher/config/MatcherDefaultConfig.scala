package com.wavesplatform.it.sync.matcher.config

import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.account.PrivateKeyAccount
import com.wavesplatform.api.http.assets.SignedIssueV1Request
import com.wavesplatform.transaction.assets.IssueTransactionV1
import com.wavesplatform.transaction.assets.exchange.AssetPair
import com.wavesplatform.it.sync._
import com.wavesplatform.matcher.market.MatcherActor
import com.wavesplatform.transaction.AssetId

import scala.util.Random

object MatcherDefaultConfig {

  import ConfigFactory._
  import com.wavesplatform.it.NodeConfigs._

  val ForbiddenAssetId = "FdbnAsset"
  val orderLimit       = 20

  val minerEnabled  = parseString(s"""
       |TN.miner.enable = yes
       |TN.miner.quorum = 0""".stripMargin)
  val minerDisabled = parseString("TN.miner.enable = no")
  val matcherConfig = parseString(s"""
                                     |TN.miner.enable = no
                                     |TN.matcher {
                                     |  enable = yes
                                     |  account = 3HmFkAoQRs4Y3PE2uR6ohN7wS4VqPBGKv7k
                                     |  bind-address = "0.0.0.0"
                                     |  order-match-tx-fee = 300000
                                     |  order-cleanup-interval = 20s
                                     |  blacklisted-assets = ["$ForbiddenAssetId"]
                                     |  balance-watching.enable = yes
                                     |  rest-order-limit=$orderLimit
                                     |}""".stripMargin)

  val Configs: Seq[Config] = (Default.last +: Random.shuffle(Default.init).take(2))
    .zip(Seq(matcherConfig, minerDisabled, minerEnabled))
    .map { case (n, o) => o.withFallback(n) }

  def issueAssetPair(issuer: PrivateKeyAccount,
                     amountAssetDecimals: Byte,
                     priceAssetDecimals: Byte): (SignedIssueV1Request, SignedIssueV1Request, AssetPair) = {
    issueAssetPair(issuer, issuer, amountAssetDecimals, priceAssetDecimals)
  }

  def issueAssetPair(amountAssetIssuer: PrivateKeyAccount,
                     priceAssetIssuer: PrivateKeyAccount,
                     amountAssetDecimals: Byte,
                     priceAssetDecimals: Byte): (SignedIssueV1Request, SignedIssueV1Request, AssetPair) = {

    val issueAmountAssetTx: IssueTransactionV1 = IssueTransactionV1
      .selfSigned(
        sender = amountAssetIssuer,
        name = Random.nextString(4).getBytes(),
        description = Random.nextString(10).getBytes(),
        quantity = someAssetAmount,
        decimals = amountAssetDecimals,
        reissuable = false,
        fee = issueFee,
        timestamp = System.currentTimeMillis()
      )
      .right
      .get

    val issuePriceAssetTx: IssueTransactionV1 = IssueTransactionV1
      .selfSigned(
        sender = priceAssetIssuer,
        name = Random.nextString(4).getBytes(),
        description = Random.nextString(10).getBytes(),
        quantity = someAssetAmount,
        decimals = priceAssetDecimals,
        reissuable = false,
        fee = issueFee,
        timestamp = System.currentTimeMillis()
      )
      .right
      .get

    if (MatcherActor.compare(Some(issuePriceAssetTx.id().arr), Some(issueAmountAssetTx.id().arr)) < 0) {
      (createSignedIssueRequest(issueAmountAssetTx),
       createSignedIssueRequest(issuePriceAssetTx),
       AssetPair(
         amountAsset = Some(issueAmountAssetTx.id()),
         priceAsset = Some(issuePriceAssetTx.id())
       ))
    } else
      issueAssetPair(amountAssetIssuer, priceAssetIssuer, amountAssetDecimals, priceAssetDecimals)
  }

  def assetPairIssuePriceAsset(issuer: PrivateKeyAccount, amountAssetId: AssetId, priceAssetDecimals: Byte): (SignedIssueV1Request, AssetPair) = {

    val issuePriceAssetTx: IssueTransactionV1 = IssueTransactionV1
      .selfSigned(
        sender = issuer,
        name = Random.nextString(4).getBytes(),
        description = Random.nextString(10).getBytes(),
        quantity = someAssetAmount,
        decimals = priceAssetDecimals,
        reissuable = false,
        fee = issueFee,
        timestamp = System.currentTimeMillis()
      )
      .right
      .get

    if (MatcherActor.compare(Some(issuePriceAssetTx.id().arr), Some(amountAssetId.arr)) < 0) {
      (createSignedIssueRequest(issuePriceAssetTx),
       AssetPair(
         amountAsset = Some(amountAssetId),
         priceAsset = Some(issuePriceAssetTx.id())
       ))
    } else
      assetPairIssuePriceAsset(issuer, amountAssetId, priceAssetDecimals)
  }

}
