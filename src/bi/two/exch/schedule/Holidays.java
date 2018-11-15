package bi.two.exch.schedule;

import java.util.Arrays;

// see https://www.stockmarketclock.com/exchanges/moex/market-holidays/2018
public enum Holidays {
//    January 1, 2017
//    January 2, 2017
//    January 3, 2017
//    January 4, 2017
//    January 5, 2017
//    January 6, 2017
//    January 7, 2017
//    February 23, 2017
//    February 24, 2017
//    March 8, 2017
//    May 1, 2017
//    May 8, 2017
//    May 9, 2017
//    June 12, 2017

    // All Moscow Exchange markets will be closed on all Russian public holidays not noted above, specifically
    // 4 November, 6 November, 30-31 December 2017,
    ru("20171104", "20171106", "20171230","20171231",
    // 1-2 January,          6-8 January 2018,                23 February, 8 and 10-11 March,                  29 April,   1 May,      5-6 May                 9 May,      10 and 12 June,         5 November.
       "20180101","20180102","20180106","20180107","20180108","20180223",  "20180308", "20180310", "20180311", "20180429", "20180501", "20180505", "20180505", "20180509", "20180610", "20180612", "20181105" );
//  January 1, 2019
//  January 2, 2019
//  January 7, 2019
//  February 23, 2019
//  March 8, 2019
//  May 1, 2019
//  May 9, 2019
//  June 12, 2019
//  November 4, 2019


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
