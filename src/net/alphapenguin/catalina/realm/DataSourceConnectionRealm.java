/*
 * Copyright 2007 Tore Andre Klock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.alphapenguin.catalina.realm;

import java.security.Principal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.sql.DataSource;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.naming.ContextBindings;

/**
 * Realm that will authenticate a user through the
 * authentication process of the DataSource itself.
 * <p>For example given a javax.sql.DataSource, the user will be granted access
 * if that user has access to open a connection through that DataSource.</p>
 * <p>The most common usage will be through the JDBC mode
 * by using a connectionURL and driverName property.</p>
 * <p><b>Note that connection pooling is inherently incompatible with this kind
 * of authentication.  Use a separate connection pool for data <i>access</i>.</b></p>
 * <p>In addition, a role query can be specified, which will cause this realm to grant the returned names
 * as roles to the authenticated principal.  The query will use the connection opened as the user.</p>
 * <p>For example, this query could work for a MySQL database if you want to grant the user roles named
 * after the catalogs to which the user has access:<br>
 * <blockquote><tt>select db from mysql.db where user = ?</tt></blockquote>
 * <br>
 * <p>This .jar file must be installed in the $CATALINA_HOME/server/lib directory of your Tomcat installation.</p>
 * <p><b>Supported properties:</b><br>
 * <dl>
 * <dt>resourceName</dt><dd>(required if used with a datasource) must be a valid JNDI name for a javax.sql.DataSource.
 * The DataSource mode will only work on DataSources that allow the use of getConnection(username,password), i.e. unpooled ones.</dd>
 * <dt>driverName</dt>
 * <dd>(required in JDBC mode) The JDBC driver's class name.</dd>
 * <dt>connectionURL</dt>
 * <dd>(required in JDBC mode) The JDBC URL to ues to connect to the database.  Should not include credentials.</dd>
 * <dt>roleQuery</dt><dd>(optional) a query that should return role names as the first column and take exactly one parameter; namely the username.</dd>
 * <dt>localDataSource</dt>
 * <dd>(optional) in datasource mode, use a local, not global datasource</dd>
 * </dl>
 * </p>
 * <p><b>Example Tomcat server.xml:</b><br>
 * <pre>
 *  &lt;Realm className="net.alphapenguin.catalina.realm.DataSourceConnectionRealm"
 *	  connectionURL="jdbc:mysql://localhost:3306/mydb"
 *	  driverName="com.mysql.jdbc.Driver"
 *	  roleQuery="select rolename from mydb.user_roles where username = ?"
 * /&gt;
 * </pre>
 * @author Tore A. Klock
 * @version 1.0
 * 
 * TODO: add support for getting roles from columns (e.g. is_admin)
 */
public class DataSourceConnectionRealm extends org.apache.catalina.realm.RealmBase {
    protected String resourceName;
    protected String roleQuery;
    protected boolean localDataSource = false;
    protected String connectionURL;
    protected String driverName;
    
    /**
     * Create a new instance of DataSourceConnectionRealm.
     */
    public DataSourceConnectionRealm() {
        super();
    }
    
    public boolean isLocalDataSource() {
        return localDataSource;
    }
    
    public void setLocalDataSource(boolean localDataSource) {
        this.localDataSource = localDataSource;
    }
    
    public String getConnectionURL() {
        return connectionURL;
    }
    
    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
    }
    
    public String getDriverName() {
        return driverName;
    }
    
    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }
    
    
    /**
     * Get the value of the roleQuery property.
     * @return the SQL query string
     */
    public String getRoleQuery() {
        return roleQuery;
    }
    
    /**
     * Set the value of the roleQuery property.
     * @param roleQuery the SQL query string
     */
    public void setRoleQuery(String roleQuery) {
        this.roleQuery = roleQuery;
    }
    
    /**
     * Get the value of the dataSourceName property.
     * @return the name of the datasource
     */
    public String getResourceName() {
        return resourceName;
    }
    
    /**
     * Set the value of the dataSourcename property.
     * @param dataSourceName The JNDI name of the data source.
     */
    public void setResourceName(String dataSourceName) {
        this.resourceName = dataSourceName;
    }
    
    /**
     * Authenticates the user by trying to open a connection using the
     * DataSource and the provided username and password.
     * @param username the username
     * @param password the password
     * @return a GenericPrincipal if successful or null on failure.
     */
    private Principal authenticateDataSource(String username, String password) {
        Principal p = null;
        if( getResourceName() == null ) {
            throw new RuntimeException("dataSourceName is not configured");
        }
        try {
            
            Context ctx = null;
            if (localDataSource) {
                ctx = ContextBindings.getClassLoader();
                ctx = (Context) ctx.lookup("comp/env");
            } else {
                StandardServer server =
                     (StandardServer) ServerFactory.getServer();
                ctx = server.getGlobalNamingContext();
            }
            DataSource dataSource = (DataSource)ctx.lookup(getResourceName());
            if( dataSource != null ) {
                Connection con = dataSource.getConnection(username, password);
                try {
                    p = authConnection(con, username, password);
                } finally {
                    if( con != null ) {
                        con.close();
                    }
                }
            } else {
                containerLog.error("Unable to find resourceName="+getResourceName());
            }
        } catch(NamingException nex) {
            containerLog.error("Error looking up dataSourceName="+getResourceName(),nex);
        } catch(SQLException sqlex) {
            /* we can log this, but otherwise we do nothing */
            containerLog.warn("SQL Exception",sqlex);
        }
        return p;
    }
    
    /**
     * Try to find roles on the connection and return a GenericPrincipal.
     * @param con
     * @param username
     * @param password
     * @return a GenericPrincipal or null if not found
     * @throws java.sql.SQLException
     */
    private Principal authConnection(Connection con, String username, String password)
         throws SQLException {
        GenericPrincipal p;
        containerLog.trace("authConnection called");
        if( con != null ) {
            if( getRoleQuery() != null ) {
                containerLog.trace("Running role query "+getRoleQuery());
                PreparedStatement ps = con.prepareStatement(getRoleQuery());
                try {
                    ps.setString(1,username);
                    ResultSet rs = ps.executeQuery();
                    try {
                        List<String> roles = new ArrayList<String>();
                        while( rs.next() ) {
                            String role = rs.getString(1);
                            containerLog.trace("Adding role "+role);
                            roles.add(role);
                        }
                        p = new GenericPrincipal(this,username,password,roles);
                    } finally {
                        rs.close();
                    }
                } finally {
                    ps.close();
                }
            } else {
                p = new GenericPrincipal(this,username,password);
            }
        } else {
            containerLog.error("Connection was null");
            p = null;
        }
        return p;
    }
    
    private Principal authenticateJDBC(String username, String password) {
        Principal p;
        containerLog.trace("authenticateJDBC called");
        try {
            // Attempt to load JDBC driver
            Class.forName(driverName);
            // Try getting a connection
            Connection con = DriverManager.getConnection(connectionURL,username,password);
            try {
                p = authConnection(con, username, password);
            } finally {
                if( con != null) {
                    con.close();
                }
            }
        } catch(ClassNotFoundException cnex) {
            containerLog.error("Cannot find JDBC driver "+driverName,cnex);
            p = null;
        } catch(SQLException sqlex) {
            containerLog.error("SQL Exception",sqlex);
            p = null;
        }
        return p;
    }
    
    @Override
    public Principal authenticate(String username, String password) {
        Principal p = null;
        containerLog.trace("authenticate("+username+",?) called");
        if( getResourceName() != null ) {
            p = authenticateDataSource(username, password);
        } else if( getConnectionURL() != null ) {
            p = authenticateJDBC(username, password);
        }
        if( p == null ) {
            containerLog.trace("Not authenticated");
            //    p = super.authenticate(username, password);
        }
        return p;
    }
    
    /**
     * Returns a short description of this implementation.
     * @return
     */
    @Override
    public String getInfo() {
        return "DataSourceConnectionRealm/1.0";
    }
    
    /**
     * Get the name of this Realm for logging purposes.
     * @return The String 'DataSourceConnectionRealm'
     */
    @Override
    protected String getName() {
        return "DataSourceConnectionRealm";
    }
    
    /**
     * Get the password for a user.
     * This implementation always returns null.
     * @param username The username.
     * @return null
     */
    @Override
    protected String getPassword(String username) {
        return null;
    }
    
    /**
     * Gets the Principal for a given username.
     * @param username the username
     * @return null
     */
    @Override
    protected Principal getPrincipal(String username) {
        return null;
    }
    
}
