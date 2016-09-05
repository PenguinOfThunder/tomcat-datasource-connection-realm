# DataSourceConnectionRealm #

## What is it? ##

Note: I created this in August 2007. It may not work with newer versions of Tomcat.

This is a custom Tomcat realm that will authenticate a user through the
authentication process of the DataSource itself.

For example given a [javax.sql.DataSource](https://docs.oracle.com/javase/7/docs/api/javax/sql/DataSource.html), the user will be granted access
if that user has access to open a connection through that DataSource.

The most common usage will be through the JDBC mode
by using a `connectionURL` and `driverName` property.

**Note that connection pooling is inherently incompatible with this kind
of authentication.  Use a separate connection pool for data *access*.**

In addition, a role query can be specified, which will cause this realm to grant the returned names
as roles to the authenticated principal.  The query will use the connection opened as the user.

For example, this query could work for a MySQL database if you want to grant the user roles named
after the catalogs to which the user has access:<br>

```sql
select db from mysql.db where user = ?
```

This .jar file must be installed in the `$CATALINA_HOME/server/lib` directory of your Tomcat installation.

### Supported properties ####

* `resourceName`
  (required if used with a datasource) must be a valid JNDI name for a `javax.sql.DataSource`.
  The DataSource mode will only work on DataSourceS that allow the use of `getConnection(username, password)`, i.e. unpooled ones.
* `driverName`
  (required in JDBC mode) The JDBC driver's class name.
* `connectionURL`
  (required in JDBC mode) The JDBC URL to ues to connect to the database.  Should not include credentials.
* `roleQuery`
  (optional) a query that should return role names as the first column and take exactly one parameter; namely the username.
* `localDataSource`
  (optional) in datasource mode, use a local, not global datasource

### Example Tomcat server.xml ###

```xml
<Realm className="net.alphapenguin.catalina.realm.DataSourceConnectionRealm"
  connectionURL="jdbc:mysql://localhost:3306/mydb"
  driverName="com.mysql.jdbc.Driver"
  roleQuery="select rolename from mydb.user_roles where username = ?"/>
```