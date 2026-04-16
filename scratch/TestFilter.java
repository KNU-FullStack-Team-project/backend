import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TestFilter {

    private static final String KOSPI_URL = "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
    private static final String KOSDAQ_URL = "https://new.real.download.dws.co.kr/common/master/kosdaq_code.mst.zip";

    public static void main(String[] args) throws Exception {
        test(KOSPI_URL, "KOSPI");
        test(KOSDAQ_URL, "KOSDAQ");
    }

    private static void test(String url, String market) throws Exception {
        System.out.println("====== " + market + " ======");
        try (ZipInputStream zis = new ZipInputStream(new URL(url).openStream())) {
            ZipEntry entry = zis.getNextEntry();
            if (entry != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(zis, "MS949"));
                String line;
                while ((line = br.readLine()) != null) {
                    byte[] bytes = line.getBytes("MS949");
                    if (bytes.length < 61) continue;
                    String shortCode = new String(bytes, 0, 9, "MS949").trim();
                    String stockName = new String(bytes, 21, 40, "MS949").trim();
                    char marketDivCode = ' ';
                    if (bytes.length > 76) {
                        marketDivCode = (char) bytes[76];
                    }
                    
                    if (shouldSkipStock(stockName, shortCode, marketDivCode)) {
                        // To avoid printing thousands of ETFs, pre-filter obvious ones before printing
                        String upper = stockName.toUpperCase();
                        if (shortCode.startsWith("5") || upper.contains("ETN") || upper.contains("스팩") || upper.contains("호") || upper.contains("KODEX") || upper.contains("TIGER") || upper.contains("KBSTAR") || upper.contains("ARIRANG")) {
                            continue;
                        }
                        System.out.println("SKIPPED [" + market + "]: " + shortCode + " | " + stockName + " | divCode = [" + marketDivCode + "] | bytes.length = " + bytes.length);
                    }
                }
            }
        }
    }

    // ORIGINAL METHOD TO TEST
    private static boolean shouldSkipStock(String name, String code, char marketDivCode) {
        if (name == null || name.isEmpty()) return true;
        String upperName = name.toUpperCase();
        if (code != null && code.startsWith("5")) return true;
        
        // 2. 시장구분 코드가 '1'(주식)이 아닌 경우
        if (marketDivCode != ' ' && marketDivCode != '1') return true;

        if (upperName.contains("부동산") || 
            upperName.contains("오피스") || 
            upperName.contains("물류") || 
            upperName.contains("하이일드") ||
            upperName.contains("파생") || 
            upperName.contains("CLASS") ||
            upperName.contains("종류") || 
            upperName.contains("공모주") ||
            upperName.contains("인프라") ||
            upperName.contains("핵심성장") ||
            upperName.contains("혁신산업") ||
            upperName.contains("포커스") ||
            upperName.contains("액티브") || 
            upperName.contains("채권") ||
            upperName.contains("그로쓰") ||
            upperName.contains("인덱스") ||
            upperName.contains("밸류")) {
            return true;
        }
        if (upperName.matches(".*[\\(\\s][A-Z,a-z].*")) {
            if (!upperName.contains("(우)") && !upperName.contains("(주)") && !upperName.contains("(전환)")) {
                return true; 
            }
        }
        return name.contains("스팩") || 
               name.contains("기업인수목적") || 
               name.contains("넥스트웨이브") || 
               name.matches(".*제\\d+호.*");
    }
}
