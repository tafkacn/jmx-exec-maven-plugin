package tafkacn;

import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author raymond.mak
 */
public class Attribute {

    @Parameter(required = true)
    public String name;
    
    @Parameter(required = true)
    public String value;

}
