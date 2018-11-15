package bi.two.exch.schedule;

import java.util.Arrays;

// see https://www.stockmarketclock.com/exchanges/moex/market-holidays/2018
public enum Holidays {
    // All Moscow Exchange markets will be closed on all Russian public holidays not noted above, specifically
    ru(
    // January 1-7,                                                                        February 23-24,         March 8,    May 1,      May 8-9,                June 12,    4 November, 6 November, 30-31 December 2017,
       "20180101", "20180102", "20180103", "20180104", "20180105", "20180106", "20180107", "20170223", "20170224", "20170308", "20170501", "20170508", "20170509", "20170612", "20171104", "20171106", "20171230","20171231",
    // 1-2 January,          6-8 January 2018,                23 February, 8 and 10-11 March,                  29 April,   1 May,      5-6 May                 9 May,      10 and 12 June,         5 November.
       "20180101","20180102","20180106","20180107","20180108","20180223",  "20180308", "20180310", "20180311", "20180429", "20180501", "20180505", "20180506", "20180509", "20180610", "20180612", "20181105",
    // 1-2 January,          7 January, 23 February, 8 March,    1 May,      9 May,      12 June,    4 November.
       "20190101","20190102","20190107","20190223",  "20190308", "20190501", "20190509", "20190612", "20191104"
    );

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
