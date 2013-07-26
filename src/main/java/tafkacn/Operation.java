package tafkacn;

import org.apache.maven.plugins.annotations.Parameter;

/**
 *
 * @author raymond.mak
 */
public class Operation {
 
    static final private String[] EMPTY_PARAMETER_ARRAY = new String[0];
    
    @Parameter(required = true)
    public String name;
    
    @Parameter
    public String[] parameters = EMPTY_PARAMETER_ARRAY;
}
