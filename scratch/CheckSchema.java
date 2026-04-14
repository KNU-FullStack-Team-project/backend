import java.sql.*;
public class CheckSchema {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "TEAM12", "team12");
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getImportedKeys(null, "TEAM12", "ORDERS");
        System.out.println("Foreign Keys for ORDERS table:");
        while (rs.next()) {
            System.out.println("FK Name: " + rs.getString("FK_NAME") +
                               ", PK Table: " + rs.getString("PKTABLE_NAME") +
                               ", FK Column: " + rs.getString("FKCOLUMN_NAME"));
        }
        conn.close();
    }
}
