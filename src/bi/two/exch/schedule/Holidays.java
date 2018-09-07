package bi.two.exch.schedule;

import java.util.Arrays;

// see https://www.stockmarketclock.com/exchanges/moex/market-holidays/2018
public enum Holidays {

    // All Moscow Exchange markets will be closed on all Russian public holidays not noted above, specifically
    // 30-31 December 2017,  1-2 January,          6-8 January 2018,                23 February, 8 and 10-11 March,                  29 April,   1 May,      5-6 May                 9 May,      10 and 12 June,         5 November.
    ru("30122017","31122017","01012018","02012018","06012018","07012018","08012018","23022018",  "08032018", "10032018", "11032018", "29042018", "01052018", "05052018", "05052018", "09052018", "10062018", "12062018", "05112018" );

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
