package com.ghasl_service.demo.config;

import com.ghasl_service.demo.model.MarketingChannel;
import com.ghasl_service.demo.model.MarketingSpend;
import com.ghasl_service.demo.repository.MarketingSpendRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Seeder for marketing spend data.
 * Creates sample marketing spend records for the current month if none exist.
 * This provides initial data for the marketing ROI gauge chart.
 * Period is stored as String in "yyyy-MM" format.
 */
@Component
public class MarketingDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketingDataSeeder.class);

    private final MarketingSpendRepository marketingSpendRepository;

    public MarketingDataSeeder(MarketingSpendRepository marketingSpendRepository) {
        this.marketingSpendRepository = marketingSpendRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        // DISABLED: No longer seeding mock marketing spend data
        // System will start with zero marketing spend and rely on user input via UI
        log.info("Marketing data seeder disabled - system will use dynamic user input only");
        
        // If you need to re-enable this for testing, uncomment the code below:
        /*
        String currentPeriod = YearMonth.now().toString(); // "yyyy-MM" format
        
        if (marketingSpendRepository.existsByPeriod(currentPeriod)) {
            log.info("Marketing spend data already exists for period: {}", currentPeriod);
            return;
        }
        
        log.info("Seeding sample marketing spend data for period: {}", currentPeriod);
        
        MarketingSpend facebookSpend = new MarketingSpend();
        facebookSpend.setPeriod(currentPeriod);
        facebookSpend.setChannel(MarketingChannel.FACEBOOK_ADS);
        facebookSpend.setAmount(new BigDecimal("150000.00"));
        facebookSpend.setCampaignName("Summer Campaign 2026");
        facebookSpend.setDescription("Facebook ads for carpet cleaning services");
        
        MarketingSpend instagramSpend = new MarketingSpend();
        instagramSpend.setPeriod(currentPeriod);
        instagramSpend.setChannel(MarketingChannel.INSTAGRAM_ADS);
        instagramSpend.setAmount(new BigDecimal("100000.00"));
        instagramSpend.setCampaignName("Visual Campaign 2026");
        instagramSpend.setDescription("Instagram ads for before/after cleaning results");
        
        MarketingSpend telegramSpend = new MarketingSpend();
        telegramSpend.setPeriod(currentPeriod);
        telegramSpend.setChannel(MarketingChannel.TELEGRAM_ADS);
        telegramSpend.setAmount(new BigDecimal("50000.00"));
        telegramSpend.setCampaignName("Telegram Channel Promotion");
        telegramSpend.setDescription("Sponsored messages in local Telegram channels");
        
        marketingSpendRepository.save(facebookSpend);
        marketingSpendRepository.save(instagramSpend);
        marketingSpendRepository.save(telegramSpend);
        
        log.info("Sample marketing spend data seeded successfully for period: {}", currentPeriod);
        */
    }
}
