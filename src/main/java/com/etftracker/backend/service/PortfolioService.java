package com.etftracker.backend.service;

import com.etftracker.backend.dto.AddHoldingRequestDTO;
import com.etftracker.backend.dto.HoldingRecordDTO;
import com.etftracker.backend.dto.PortfolioSummaryDTO;
import com.etftracker.backend.dto.PortfolioSummaryDTO.PortfolioHoldingDTO;
import com.etftracker.backend.model.AssetInfo;
import com.etftracker.backend.model.RealizedPnL;
import com.etftracker.backend.model.UserPortfolio;
import com.etftracker.backend.repository.AssetInfoRepository;
import com.etftracker.backend.repository.PriceHistoryRepository;
import com.etftracker.backend.repository.RealizedPnLRepository;
import com.etftracker.backend.repository.UserPortfolioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 投資組合業務邏輯服務
 * 計算使用者的持倉市值、平均成本、未實現損益與各資產佔比，
 * 同時提供新增、刪除交易明細的寫入操作
 */
@Service
public class PortfolioService {

    @Autowired
    private UserPortfolioRepository portfolioRepository;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private AssetInfoRepository assetInfoRepository;

    @Autowired
    private RealizedPnLRepository realizedPnLRepository;

    /**
     * 查詢所有交易明細（raw records），依資產代號與買入日期排序
     *
     * @return 所有持倉交易紀錄列表
     */
    public List<HoldingRecordDTO> getAllHoldingRecords(String owner) {
        return portfolioRepository.findAllByOwnerOrderByAsset_TickerAscBuyDateAsc(owner)
                .stream()
                .map(p -> {
                    BigDecimal totalCost = p.getQuantity().multiply(p.getUnitPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    return new HoldingRecordDTO(
                            p.getPortfolioId(),
                            p.getAsset().getTicker(),
                            p.getAsset().getName(),
                            p.getBuyDate(),
                            p.getQuantity(),
                            p.getUnitPrice(),
                            totalCost,
                            p.getOwner(),
                            p.getFee() != null ? p.getFee() : BigDecimal.ZERO
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 新增一筆買入持倉紀錄
     *
     * @param request 包含 ticker、buyDate、quantity、unitPrice、fee 的請求 DTO
     * @return 新增成功後的交易明細 DTO
     * @throws IllegalArgumentException 若找不到對應的資產代號
     */
    @Transactional
    public HoldingRecordDTO addHolding(AddHoldingRequestDTO request) {
        // 驗證資產是否存在
        AssetInfo asset = assetInfoRepository.findByTicker(request.getTicker().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到標的代號 \"" + request.getTicker() + "\"，請確認輸入的代號是否正確。"));

        // 估算或套用手續費
        BigDecimal fee = request.getFee();
        if (fee == null) {
            BigDecimal volume = request.getQuantity().multiply(request.getUnitPrice());
            BigDecimal calculatedFee = volume.multiply(new BigDecimal("0.001425"))
                    .multiply(new BigDecimal("0.6")) // 富邦電子單 6 折
                    .setScale(0, RoundingMode.HALF_UP);
            boolean isOddShare = request.getQuantity().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0;
            if (isOddShare) {
                fee = calculatedFee.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : calculatedFee;
            } else {
                fee = calculatedFee.compareTo(new BigDecimal("20")) < 0 ? new BigDecimal("20") : calculatedFee;
            }
        }

        // 建立新紀錄
        UserPortfolio newRecord = new UserPortfolio();
        newRecord.setAsset(asset);
        newRecord.setBuyDate(request.getBuyDate());
        newRecord.setQuantity(request.getQuantity());
        newRecord.setUnitPrice(request.getUnitPrice());
        newRecord.setFee(fee);
        newRecord.setOwner(request.getOwner() != null ? request.getOwner().trim() : "自己");

        UserPortfolio saved = portfolioRepository.save(newRecord);

        BigDecimal totalCost = saved.getQuantity().multiply(saved.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);

        return new HoldingRecordDTO(
                saved.getPortfolioId(),
                saved.getAsset().getTicker(),
                saved.getAsset().getName(),
                saved.getBuyDate(),
                saved.getQuantity(),
                saved.getUnitPrice(),
                totalCost,
                saved.getOwner(),
                saved.getFee()
        );
    }

    /**
     * 執行賣出持倉交易
     * 採用先進先出法 (FIFO) 扣減庫存，並計算該筆交易實現的損益寫入 REALIZED_PNL
     *
     * @param request 包含 ticker、sellDate (使用 buyDate 承載)、quantity、unitPrice、owner 的請求 DTO
     * @return 已實現損益記錄的實體
     */
    @Transactional
    public RealizedPnL sellHolding(AddHoldingRequestDTO request) {
        String ticker = request.getTicker().trim().toUpperCase();
        String owner = request.getOwner() != null ? request.getOwner().trim() : "自己";

        // 1. 撈取該成員在此標的所有買入紀錄 (依買入日期由舊到新排序，以便進行 FIFO)
        List<UserPortfolio> holdings = portfolioRepository.findAllByOwnerAndAsset_TickerOrderByBuyDateAsc(owner, ticker);
        if (holdings.isEmpty()) {
            throw new IllegalArgumentException("您目前未持有標的 \"" + ticker + "\" 的庫存，無法進行賣出。");
        }

        // 2. 防呆：驗證賣出股數是否大於當前總持股數
        BigDecimal totalSharesAvailable = holdings.stream()
                .map(UserPortfolio::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSharesAvailable.compareTo(request.getQuantity()) < 0) {
            throw new IllegalArgumentException(String.format(
                    "賣出數量 (%s) 大於目前所持有的庫存數量 (%s)，無法執行交易！",
                    request.getQuantity(), totalSharesAvailable));
        }

        // 3. 計算當前持有此標的的加權平均成本 (含買入手續費)
        BigDecimal totalCostBasis = holdings.stream()
                .map(h -> h.getQuantity().multiply(h.getUnitPrice()).add(h.getFee() != null ? h.getFee() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageBuyPrice = totalCostBasis.divide(totalSharesAvailable, 4, RoundingMode.HALF_UP);

        // 4. 計算此筆賣出的手續費與證交稅
        BigDecimal sellFee = request.getFee();
        BigDecimal sellVolume = request.getQuantity().multiply(request.getUnitPrice());
        if (sellFee == null) {
            BigDecimal calculatedFee = sellVolume.multiply(new BigDecimal("0.001425"))
                    .multiply(new BigDecimal("0.6")) // 富邦電子單 6 折
                    .setScale(0, RoundingMode.HALF_UP);
            boolean isOddShare = request.getQuantity().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0;
            if (isOddShare) {
                sellFee = calculatedFee.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : calculatedFee;
            } else {
                sellFee = calculatedFee.compareTo(new BigDecimal("20")) < 0 ? new BigDecimal("20") : calculatedFee;
            }
        }
        
        // 估算證交稅 (ETF: 0.1%, 股票: 0.3%)
        BigDecimal taxRate = ticker.startsWith("00") ? new BigDecimal("0.001") : new BigDecimal("0.003");
        BigDecimal sellTax = sellVolume.multiply(taxRate).setScale(0, RoundingMode.HALF_UP);

        // 5. 計算已實現淨損益 = 賣出淨得 (賣出總額 - 賣出手續費 - 證交稅) - 買入總成本 (股數 * 平均買入均價)
        BigDecimal costOfSharesSold = request.getQuantity().multiply(averageBuyPrice);
        BigDecimal netSellValue = sellVolume.subtract(sellFee).subtract(sellTax);
        BigDecimal realizedPnLValue = netSellValue.subtract(costOfSharesSold).setScale(2, RoundingMode.HALF_UP);

        // 6. 寫入已實現損益紀錄表
        RealizedPnL realizedRecord = new RealizedPnL();
        realizedRecord.setTicker(ticker);
        realizedRecord.setAssetName(holdings.get(0).getAsset().getName());
        realizedRecord.setSellDate(request.getBuyDate()); // 借用 buyDate 當作賣出日期
        realizedRecord.setQuantity(request.getQuantity());
        realizedRecord.setSellPrice(request.getUnitPrice());
        realizedRecord.setAverageBuyPrice(averageBuyPrice);
        realizedRecord.setRealizedPnL(realizedPnLValue);
        realizedRecord.setFee(sellFee);
        realizedRecord.setTax(sellTax);
        realizedRecord.setOwner(owner);
        RealizedPnL savedRecord = realizedPnLRepository.save(realizedRecord);

        // 7. 執行先進先出 (FIFO) 扣減庫存邏輯
        BigDecimal remainingToSell = request.getQuantity();
        List<UserPortfolio> toSave = new ArrayList<>();
        List<UserPortfolio> toDelete = new ArrayList<>();

        for (UserPortfolio h : holdings) {
            if (remainingToSell.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal qty = h.getQuantity();
            if (qty.compareTo(remainingToSell) <= 0) {
                // 此筆買入數量不夠扣或剛好，整筆刪除
                remainingToSell = remainingToSell.subtract(qty);
                toDelete.add(h);
            } else {
                // 此筆夠扣，扣減部分股數
                // 注意：扣減股數時，手續費亦等比例扣減
                BigDecimal ratio = qty.subtract(remainingToSell).divide(qty, 4, RoundingMode.HALF_UP);
                if (h.getFee() != null) {
                    h.setFee(h.getFee().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
                }
                h.setQuantity(qty.subtract(remainingToSell));
                toSave.add(h);
                remainingToSell = BigDecimal.ZERO;
            }
        }

        if (!toSave.isEmpty()) {
            portfolioRepository.saveAll(toSave);
        }
        if (!toDelete.isEmpty()) {
            portfolioRepository.deleteAll(toDelete);
        }

        System.out.printf("[Portfolio] 成員【%s】已完成 %s 賣出自癒扣減。賣出 %s 股，單價 %s，均價 %s，手續費 %s，稅金 %s，已實現淨損益: NT$ %s%n",
                owner, ticker, request.getQuantity(), request.getUnitPrice(), averageBuyPrice, sellFee, sellTax, realizedPnLValue);

        return savedRecord;
    }

    /**
     * 編輯已存持倉交易明細
     *
     * @param portfolioId 持倉紀錄 ID
     * @param request 包含 ticker, buyDate, quantity, unitPrice, fee, owner 的請求 DTO
     * @return 編輯成功後的 HoldingRecordDTO
     */
    @Transactional
    public HoldingRecordDTO updateHolding(Long portfolioId, AddHoldingRequestDTO request) {
        UserPortfolio record = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("找不到 ID 為 " + portfolioId + " 的持倉紀錄。"));

        // 驗證標的是否存在
        AssetInfo asset = assetInfoRepository.findByTicker(request.getTicker().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到標的代號 \"" + request.getTicker() + "\"，請確認代號是否正確。"));

        // 估算或套用手續費
        BigDecimal fee = request.getFee();
        if (fee == null) {
            BigDecimal volume = request.getQuantity().multiply(request.getUnitPrice());
            BigDecimal calculatedFee = volume.multiply(new BigDecimal("0.001425"))
                    .multiply(new BigDecimal("0.6"))
                    .setScale(0, RoundingMode.HALF_UP);
            boolean isOddShare = request.getQuantity().remainder(new BigDecimal("1000")).compareTo(BigDecimal.ZERO) != 0;
            if (isOddShare) {
                fee = calculatedFee.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : calculatedFee;
            } else {
                fee = calculatedFee.compareTo(new BigDecimal("20")) < 0 ? new BigDecimal("20") : calculatedFee;
            }
        }

        record.setAsset(asset);
        record.setBuyDate(request.getBuyDate());
        record.setQuantity(request.getQuantity());
        record.setUnitPrice(request.getUnitPrice());
        record.setFee(fee);
        record.setOwner(request.getOwner() != null ? request.getOwner().trim() : "自己");

        UserPortfolio saved = portfolioRepository.save(record);
        BigDecimal totalCost = saved.getQuantity().multiply(saved.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);

        return new HoldingRecordDTO(
                saved.getPortfolioId(),
                saved.getAsset().getTicker(),
                saved.getAsset().getName(),
                saved.getBuyDate(),
                saved.getQuantity(),
                saved.getUnitPrice(),
                totalCost,
                saved.getOwner(),
                saved.getFee()
        );
    }

    /**
     * 查詢指定成員的所有歷史賣出已實現損益明細
     */
    public List<RealizedPnL> getRealizedHistory(String owner) {
        return realizedPnLRepository.findAllByOwnerOrderBySellDateDesc(owner);
    }

    /**
     * 刪除指定 ID 的持倉交易紀錄
     *
     * @param portfolioId 要刪除的持倉紀錄 ID
     * @throws IllegalArgumentException 若找不到對應的紀錄
     */
    @Transactional
    public void deleteHolding(Long portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new IllegalArgumentException("找不到 ID 為 " + portfolioId + " 的持倉紀錄。");
        }
        portfolioRepository.deleteById(portfolioId);
    }

    /**
     * 取得使用者的投資組合總覽
     * 依資產分組計算持股數、平均成本，並查詢最新收盤價計算市值與損益
     *
     * @return 投資組合總覽 PortfolioSummaryDTO
     */
    public PortfolioSummaryDTO getPortfolioSummary(String owner) {
        // 1. 取得所有持倉記錄
        List<UserPortfolio> allHoldings = portfolioRepository.findAllByOwnerOrderByAsset_TickerAscBuyDateAsc(owner);

        if (allHoldings.isEmpty()) {
            BigDecimal totalRealizedPnL = realizedPnLRepository.sumRealizedPnLByOwner(owner);
            PortfolioSummaryDTO summary = new PortfolioSummaryDTO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    Collections.emptyList()
            );
            summary.setTotalRealizedPnL(totalRealizedPnL != null ? totalRealizedPnL : BigDecimal.ZERO);
            return summary;
        }

        // 2. 依 ticker 分組統計
        Map<String, List<UserPortfolio>> grouped = allHoldings.stream()
                .collect(Collectors.groupingBy(p -> p.getAsset().getTicker()));

        List<PortfolioHoldingDTO> holdingDTOs = new ArrayList<>();
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;

        for (Map.Entry<String, List<UserPortfolio>> entry : grouped.entrySet()) {
            String ticker = entry.getKey();
            List<UserPortfolio> positions = entry.getValue();
            String assetName = positions.get(0).getAsset().getName();

            // 計算該資產的總持股數與加權平均成本 (含買入手續費)
            BigDecimal totalShares = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (UserPortfolio pos : positions) {
                BigDecimal qty = pos.getQuantity();
                BigDecimal price = pos.getUnitPrice();
                BigDecimal posFee = pos.getFee() != null ? pos.getFee() : BigDecimal.ZERO;
                totalShares = totalShares.add(qty);
                totalCost = totalCost.add(qty.multiply(price).add(posFee));
            }

            BigDecimal averageCost = BigDecimal.ZERO;
            if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
                averageCost = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);
            }

            // 查詢最新收盤價（取資料庫中該 ticker 最新一筆）
            BigDecimal latestPrice = priceHistoryRepository
                    .findTopByAsset_TickerOrderByTradeDateDesc(ticker)
                    .map(ph -> ph.getClosingPrice())
                    .orElse(averageCost); // 若無價格資料，以成本代替避免 NPE

            // 計算市值與損益
            BigDecimal marketValue = totalShares.multiply(latestPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costBasis = totalCost.setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPnL = marketValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedReturnRate = BigDecimal.ZERO;
            if (costBasis.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedReturnRate = unrealizedPnL.divide(costBasis, 4, RoundingMode.HALF_UP);
            }

            totalMarketValue = totalMarketValue.add(marketValue);
            totalCostBasis = totalCostBasis.add(costBasis);

            holdingDTOs.add(new PortfolioHoldingDTO(
                    ticker,
                    assetName,
                    totalShares.setScale(4, RoundingMode.HALF_UP),
                    averageCost,
                    latestPrice,
                    marketValue,
                    costBasis,
                    unrealizedPnL,
                    unrealizedReturnRate,
                    BigDecimal.ZERO // 佔比先設為 0，等最終市值算出後再計算
            ));
        }

        // 3. 計算各資產佔比
        BigDecimal finalTotalValue = totalMarketValue;
        if (finalTotalValue.compareTo(BigDecimal.ZERO) > 0) {
            holdingDTOs = holdingDTOs.stream().map(h -> {
                BigDecimal weight = h.getMarketValue().divide(finalTotalValue, 4, RoundingMode.HALF_UP);
                h.setPortfolioWeight(weight);
                return h;
            }).collect(Collectors.toList());
        }

        // 4. 計算整體未實現損益與報酬率
        BigDecimal totalUnrealizedPnL = totalMarketValue.subtract(totalCostBasis).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalUnrealizedReturnRate = BigDecimal.ZERO;
        if (totalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            totalUnrealizedReturnRate = totalUnrealizedPnL.divide(totalCostBasis, 4, RoundingMode.HALF_UP);
        }

        BigDecimal totalRealizedPnL = realizedPnLRepository.sumRealizedPnLByOwner(owner);
        PortfolioSummaryDTO summary = new PortfolioSummaryDTO(
                totalCostBasis,
                totalMarketValue.setScale(2, RoundingMode.HALF_UP),
                totalUnrealizedPnL,
                totalUnrealizedReturnRate,
                holdingDTOs
        );
        summary.setTotalRealizedPnL(totalRealizedPnL != null ? totalRealizedPnL : BigDecimal.ZERO);
        return summary;
    }

    /**
     * 取得系統中現存所有不重複的擁有人清單，並預設至少包含 "自己"
     */
    @Transactional(readOnly = true)
    public List<String> getUniqueOwners() {
        List<String> owners = portfolioRepository.findDistinctOwners();
        if (owners == null || owners.isEmpty()) {
            return Collections.singletonList("自己");
        }
        return owners;
    }
}
