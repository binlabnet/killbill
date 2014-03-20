/*
 * Copyright 2014 The Billing Project, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.invoice.usage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.DefaultInternationalPrice;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.DefaultTier;
import org.killbill.billing.catalog.DefaultTieredBlock;
import org.killbill.billing.catalog.DefaultUnit;
import org.killbill.billing.catalog.DefaultUsage;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.UsageInvoiceItem;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.usage.api.user.DefaultRolledUpUsage;
import org.killbill.billing.util.callcontext.TenantContext;
import org.mockito.Mockito;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestContiguousIntervalConsumableInArrear extends InvoiceTestSuiteNoDB {

    private int BCD;
    private UUID accountId;
    private UUID bundleId;
    private UUID subscriptionId;
    private UUID invoiceId;
    private String planName;
    private String phaseName;
    private Currency currency;
    private String usageName;

    private UsageUserApi mockUsageUserApi;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        super.beforeClass();
        BCD = 15;
        usageName = "foo";
        accountId = UUID.randomUUID();
        bundleId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        planName = "planName";
        phaseName = "phaseName";
        currency = Currency.BTC;
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() {
        super.beforeMethod();
        // Default invoice test binding;
        this.mockUsageUserApi = usageUserApi;
    }

    @Test(groups = "fast")
    public void testComputeToBeBilledUsage() {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate endDate = new LocalDate(2014, 04, 20);

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 1000, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTier(block);
        final DefaultUsage usage = createDefaultUsage(usageName, tier);

        final LocalDate targetDate = startDate.plusDays(1);
        final ContiguousIntervalConsumableInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                           createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC)));

        final List<InvoiceItem> existingUsage = Lists.newArrayList();
        final UsageInvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii1);
        final UsageInvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii2);

        // Will be ignored as is starts one day earlier.
        final UsageInvoiceItem ii3 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate.minusDays(1), endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii3);

        // Will be ignored as it is for a different udsage section
        final UsageInvoiceItem ii4 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, "other", startDate, endDate, BigDecimal.TEN, currency);
        existingUsage.add(ii4);

        // Will be ignored because non usage item
        final FixedPriceInvoiceItem ii5 = new FixedPriceInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, startDate, BigDecimal.TEN, currency);
        existingUsage.add(ii5);

        final BigDecimal result = intervalConsumableInArrear.computeBilledUsage(startDate, endDate, existingUsage);
        assertEquals(result, BigDecimal.TEN.add(BigDecimal.TEN));
    }

    @Test(groups = "fast")
    public void testComputeBilledUsage() throws CatalogApiException {

        final DefaultTieredBlock block1 = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier1 = createDefaultTier(block1);

        final DefaultTieredBlock block2 = createDefaultTieredBlock("unit", 1000, 100, BigDecimal.ONE);
        final DefaultTier tier2 = createDefaultTier(block2);
        final DefaultUsage usage = createDefaultUsage(usageName, tier1, tier2);

        final LocalDate targetDate = new LocalDate(2014, 03, 20);
        final ContiguousIntervalConsumableInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, false,
                                                                                                                           createMockBillingEvent(targetDate.toDateTimeAtStartOfDay(DateTimeZone.UTC)));

        final BigDecimal result = intervalConsumableInArrear.computeToBeBilledUsage(new BigDecimal("5325"), "unit");

        // 5000 = 1000 (tier1) + 4325 (tier2) => 10 + 5 = 15
        assertEquals(result, new BigDecimal("15"));
    }

    @Test(groups = "fast")
    public void testComputeMissingItems() throws CatalogApiException {

        final LocalDate startDate = new LocalDate(2014, 03, 20);
        final LocalDate firstBCDDate = new LocalDate(2014, 04, 15);
        final LocalDate endDate = new LocalDate(2014, 05, 15);

        // 2 items for startDate - firstBCDDate
        final RolledUpUsage usage1 = new DefaultRolledUpUsage(subscriptionId, "unit", startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), firstBCDDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), new BigDecimal("130"));
        final RolledUpUsage usage2 = new DefaultRolledUpUsage(subscriptionId, "unit", startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), firstBCDDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), new BigDecimal("271"));

        // 1 items for firstBCDDate - endDate
        final RolledUpUsage usage3 = new DefaultRolledUpUsage(subscriptionId, "unit", firstBCDDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC), new BigDecimal("199"));

        final List<RolledUpUsage> usages = ImmutableList.<RolledUpUsage>builder().add(usage1).add(usage2).add(usage3).build();
        this.mockUsageUserApi = createMockUsageUserApi(usages);

        final DefaultTieredBlock block = createDefaultTieredBlock("unit", 100, 10, BigDecimal.ONE);
        final DefaultTier tier = createDefaultTier(block);
        final DefaultUsage usage = createDefaultUsage(usageName, tier);

        final LocalDate targetDate = endDate;

        final BillingEvent event1 = createMockBillingEvent(startDate.toDateTimeAtStartOfDay(DateTimeZone.UTC));
        final BillingEvent event2 = createMockBillingEvent(endDate.toDateTimeAtStartOfDay(DateTimeZone.UTC));

        final ContiguousIntervalConsumableInArrear intervalConsumableInArrear = createContiguousIntervalConsumableInArrear(usage, targetDate, true, event1, event2);

        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        InvoiceItem ii1 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), startDate, firstBCDDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii1);

        InvoiceItem ii2 = new UsageInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, usage.getName(), firstBCDDate, endDate, BigDecimal.ONE, currency);
        invoiceItems.add(ii2);

        final List<InvoiceItem> result = intervalConsumableInArrear.computeMissingItems(invoiceItems);
        assertEquals(result.size(), 2);
        // Invoiced for 1 BTC and used 130 + 271 = 401 => 5 blocks => 5 BTC so remaining piece should be 4 BTC
        assertTrue(result.get(0).getAmount().compareTo(new BigDecimal("4.0")) == 0);
        assertEquals(result.get(0).getCurrency(), Currency.BTC);
        assertEquals(result.get(0).getAccountId(), accountId);
        assertEquals(result.get(0).getBundleId(), bundleId);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getPlanName(), planName);
        assertEquals(result.get(0).getPhaseName(), phaseName);
        assertEquals(result.get(0).getUsageName(), usage.getName());
        assertTrue(result.get(0).getStartDate().compareTo(startDate) == 0);
        assertTrue(result.get(0).getEndDate().compareTo(firstBCDDate) == 0);

        // Invoiced for 1 BTC and used 199  => 2 blocks => 2 BTC so remaining piece should be 1 BTC
        assertTrue(result.get(1).getAmount().compareTo(new BigDecimal("1.0")) == 0);
        assertEquals(result.get(1).getCurrency(), Currency.BTC);
        assertEquals(result.get(1).getAccountId(), accountId);
        assertEquals(result.get(1).getBundleId(), bundleId);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getPlanName(), planName);
        assertEquals(result.get(1).getPhaseName(), phaseName);
        assertEquals(result.get(1).getUsageName(), usage.getName());
        assertTrue(result.get(1).getStartDate().compareTo(firstBCDDate) == 0);
        assertTrue(result.get(1).getEndDate().compareTo(endDate) == 0);

    }

    private UsageUserApi createMockUsageUserApi(final List<RolledUpUsage> returnValue) {
        final UsageUserApi result = Mockito.mock(UsageUserApi.class);
        Mockito.when(result.getAllUsageForSubscription(Mockito.<UUID>any(), Mockito.<Set<String>>any(), Mockito.<List<DateTime>>any(), Mockito.<TenantContext>any())).thenReturn(returnValue);
        return result;
    }

    private ContiguousIntervalConsumableInArrear createContiguousIntervalConsumableInArrear(final DefaultUsage usage, final LocalDate targetDate, final boolean closedInterval, final BillingEvent... events) {
        final ContiguousIntervalConsumableInArrear intervalConsumableInArrear = new ContiguousIntervalConsumableInArrear(usage, invoiceId, mockUsageUserApi, targetDate, callContext);
        for (BillingEvent event : events) {
            intervalConsumableInArrear.addBillingEvent(event);
        }
        intervalConsumableInArrear.build(closedInterval);
        return intervalConsumableInArrear;
    }

    private DefaultUsage createDefaultUsage(final String usageName, final DefaultTier... tiers) {
        final DefaultUsage usage = new DefaultUsage();
        usage.setName(usageName);
        usage.setBillingMode(BillingMode.IN_ARREAR);
        usage.setUsageType(UsageType.CONSUMABLE);
        usage.setBillingPeriod(BillingPeriod.MONTHLY);
        usage.setTiers(tiers);
        return usage;
    }

    private DefaultTier createDefaultTier(final DefaultTieredBlock... blocks) {
        DefaultTier tier = new DefaultTier();
        tier.setBlocks(blocks);
        return tier;
    }

    private DefaultTieredBlock createDefaultTieredBlock(final String unit, final int size, final int max, final BigDecimal btcPrice) {
        final DefaultTieredBlock block = new DefaultTieredBlock();
        block.setUnit(new DefaultUnit().setName(unit));
        block.setSize(new Double(size));

        final DefaultPrice[] prices = new DefaultPrice[1];
        prices[0] = new DefaultPrice();
        prices[0].setCurrency(Currency.BTC).setValue(btcPrice);

        block.setPrice(new DefaultInternationalPrice().setPrices(prices));
        block.setMax(new Double(max));
        return block;
    }

    private BillingEvent createMockBillingEvent(DateTime effectiveDate) {
        final BillingEvent result = Mockito.mock(BillingEvent.class);
        Mockito.when(result.getCurrency()).thenReturn(Currency.BTC);
        Mockito.when(result.getBillCycleDayLocal()).thenReturn(BCD);
        Mockito.when(result.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(result.getEffectiveDate()).thenReturn(effectiveDate);

        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getId()).thenReturn(accountId);
        Mockito.when(result.getAccount()).thenReturn(account);

        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);
        Mockito.when(result.getSubscription()).thenReturn(subscription);

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("planName");
        Mockito.when(result.getPlan()).thenReturn(plan);

        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        Mockito.when(phase.getName()).thenReturn("phaseName");
        Mockito.when(result.getPlanPhase()).thenReturn(phase);

        return result;
    }
}
