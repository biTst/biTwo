package bi.two.exch.schedule;

import java.util.Arrays;




// see https://www.stockmarketclock.com/exchanges/moex/market-holidays/2018
public enum Holidays {
    // All Moscow Exchange markets will be closed on all Russian public holidays not noted above, specifically
    ru( new String[]{
    // https://www.moex.com/a3368
    // January 1, January 7-8,             February 23, March 8,    May 2, May 3,           May 9,      June 13,    November 4,
       "20160101", "20160107", "20160108", "20160223",  "20160308", "20160502", "20160503", "20160509", "20160613", "20161104",
    // https://www.moex.com/a3791
    // January 1-7,            February 23, March 8,    May 1,      May 8-9,                June 12,    4 November, 6 November, 30-31 December,
       "20170101", "20170102", "20170223",  "20170308", "20170501", "20170508", "20170509", "20170612", "20171104", "20171106", "20171230","20171231",
    // 1-2 January,          6-8 January,                     23 February, 8 and 10-11 March,                  29 April,   1 May,      5-6 May                 9 May,      10 and 12 June,         5 November, 31 December
       "20180101","20180102","20180106","20180107","20180108","20180223",  "20180308", "20180310", "20180311", "20180429", "20180501", "20180505", "20180506", "20180509", "20180610", "20180612", "20181105", "20181231",
    // 1-2 January,          7 January, 23 February, 8 March,    1 May,      9 May,      12 June,    4 November
       "20190101","20190102","20190107","20190223",  "20190308", "20190501", "20190509", "20190612", "20191104"
            },
        new String[]{
       "20160220",
       "20180428", "20180609", "20181229"
        }
    );

    private final String[] m_holidays;
    private final String[] m_working;

    /** @param working when weekend days are working days */
    Holidays(String[] holidays, String[] working) {
        Arrays.sort(holidays); // just in case
        Arrays.sort(working); // just in case
        m_holidays = holidays;
        m_working = working;
    }

    public boolean isHoliday(String dateFormatted) {
        int index = Arrays.binarySearch(m_holidays, dateFormatted);
        return (index >= 0);
    }

    public boolean isWorking(String dateFormatted) {
        int index = Arrays.binarySearch(m_working, dateFormatted);
        return (index >= 0);
    }
}
