import java.sql.*;
public class CheckDb {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "TEAM12", "team12");
        
        System.out.println("Checking Account 502...");
        PreparedStatement ps1 = conn.prepareStatement("SELECT count(*) FROM accounts WHERE account_id = 502");
        ResultSet rs1 = ps1.executeQuery();
        if (rs1.next()) System.out.println("Account 502 count: " + rs1.getInt(1));
        
        System.out.println("Checking Stock for 000100...");
        PreparedStatement ps2 = conn.prepareStatement("SELECT stock_id, stock_name, market_type FROM stock WHERE stock_code = '000100'");
        ResultSet rs2 = ps2.executeQuery();
        if (rs2.next()) {
            System.out.println("Stock 000100 ID: " + rs2.getLong(1) + ", Name: " + rs2.getString(2) + ", Type: " + rs2.getString(3));
        } else {
            System.out.println("Stock 000100 NOT FOUND!");
        }
        
        conn.close();
    }
}
