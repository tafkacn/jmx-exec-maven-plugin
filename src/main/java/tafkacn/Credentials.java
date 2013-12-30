package tafkacn;

import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author Paul Wellner Bou <paul@wellnerbou.de>
 */
public class Credentials {

	@Parameter
	public String user;

	@Parameter
	public String password;
}
