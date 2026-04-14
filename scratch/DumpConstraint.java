import java.sql.*;
public class DumpConstraint {
    public static void main(String[] args) throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@teacherdev09.kro.kr:8877:xe", "team12", "12345");
        
        System.out.println("Checking Constraint FK4JG39JA0RJVJYYDIBGTBP8B6B...");
        DatabaseMetaData meta = conn.getMetaData();
        
        // Find which table has this constraint and what it points to
        String sql = "SELECT table_name, constraint_name, r_constraint_name " +
                     "FROM all_constraints " +
                     "WHERE constraint_name = 'FK4JG39JA0RJVJYYDIBGTBP8B6B' AND owner = 'TEAM12'";
        
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            String tableName = rs.getString(1);
            String rConstraintName = rs.getString(3);
            System.out.println("Constraint on table: " + tableName + ", Points to: " + rConstraintName);
            
            // Find parent table
            String sql2 = "SELECT table_name FROM all_constraints WHERE constraint_name = ? AND owner = 'TEAM12'";
            PreparedStatement ps2 = conn.prepareStatement(sql2);
            ps2.setString(1, rConstraintName);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                System.out.println("Parent table: " + rs2.getString(1));
            }
        } else {
            System.out.println("Constraint NOT FOUND in TEAM12 schema.");
        }
        conn.close();
    }
}
