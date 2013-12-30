jmx-exec-maven-plugin
=====================

Maven plugin for setting attributes and invoking operations on JMX MBeans

Example
-------

This is an example of a configuration in the pom.xml.

	<plugin>
		<groupId>tafkacn</groupId>
		<artifactId>jmx-exec-maven-plugin</artifactId>
		<version>1.0-SNAPSHOT</version>
		<configuration>
			<servers>
				<server>
					<host>${jmx.host}</host>
					<jndiPort>${jmx.port}</jndiPort>
					<credentials>
						<user>${jmx.user}</user>
						<password>${jmx.password}</password>
					</credentials>
				</server>
			</servers>
			<objectName>${jmx.objectName}</objectName>
			<operations>
				<operation>
					<name>${jmx.operationName}</name>
				</operation>
			</operations>
		</configuration>
	</plugin>

The credentials are supported since version 1.1 and are optional.

This would be the command to run this:

	mvn tafkacn:jmx-exec-maven-plugin:jmx-exec


