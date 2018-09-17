package bi.two.exch.schedule;

import java.util.Arrays;

// see https://www.stockmarketclock.com/exchanges/moex/market-holidays/2018
public enum Holidays {

    // All Moscow Exchange markets will be closed on all Russian public holidays not noted above, specifically
    // 30-31 December 2017,  1-2 January,          6-8 January 2018,                23 February, 8 and 10-11 March,                  29 April,   1 May,      5-6 May                 9 May,      10 and 12 June,         5 November.
    ru("20171230","20171231","20180101","20180102","20180106","20180107","20180108","20180223",  "20180308", "20180310", "20180311", "20180429", "20180501", "20180505", "20180505", "20180509", "20180610", "20180612", "20181105" );

    private final String[] m_holidays;

    Holidays(String... holidays) {
        Arrays.sort(holidays); // just in case
        m_holidays = holidays;
    }

    public boolean isHoliday(String dateFormatted) {
        int index = Arrays.binarySearch(m_holidays, dateFormatted);
        return (index >= 0);
    }
}
