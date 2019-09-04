package wrx.xing;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.text.MessageFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The type Config file check.
 *
 * @author wangruxing
 * @date 2019-09-04 15:34
 */
@Mojo(name = "envConfigFileCheck", defaultPhase = LifecyclePhase.COMPILE)
public class EnvConfigFileCheck extends AbstractMojo {

    /**
     * project auto reject
     */
    @Parameter(defaultValue = "${project}", required = true)
    private MavenProject project;

    /**
     * DFT_ENV_CONFIG_FILE_NAME
     */
    private final String DFT_ENV_CONFIG_FILE_NAME = "env-config-[\\S]*.properties";

    /**
     * 环境配置文件名称
     */
    @Parameter(defaultValue = "${envConfigFileName}")
    private String envConfigFileName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            long start = System.currentTimeMillis();
            propertyFileAlignCheck();
            System.out.println("EnvConfigFileCheck pass. cost " + (System.currentTimeMillis() - start) + " ms.");
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("EnvConfigFileCheck cause error, but ignore.");
            // ignore
        }
    }

    /**
     * 环境配置文件对齐检查
     *
     * @throws MojoExecutionException
     */
    private void propertyFileAlignCheck() throws MojoExecutionException {

        try {
            if (StringUtils.isBlank(envConfigFileName)) {
                envConfigFileName = DFT_ENV_CONFIG_FILE_NAME;
            } else if (!DFT_ENV_CONFIG_FILE_NAME.equals(envConfigFileName)) {
                envConfigFileName = StringUtils.replace(envConfigFileName, "*", "[\\S]*");
            }
            Build build = project.getBuild();
            File sourceDir = new File(build.getSourceDirectory());
            String resourcePath = sourceDir.getParent() + File.separator + "resources";
            File resourceDir = new File(resourcePath);
            int envConfigFileCount;
            File[] envConfigFiles = resourceDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".properties") && Pattern.matches(envConfigFileName, name);
                }
            });

            if (envConfigFiles == null || ((envConfigFileCount = envConfigFiles.length) == 0)) {
                throw new IOException();
            }

            fileLineNumberCheck(envConfigFiles);

            OrderedProperties[] envConfigProperties = new OrderedProperties[envConfigFileCount];
            for (int i = 0; i < envConfigFileCount; i++) {
                envConfigProperties[i] = new OrderedProperties();
                envConfigProperties[i].load(new FileInputStream(envConfigFiles[i]));
            }

            configurationItemNumberCheck(envConfigProperties);
            configurationItemCheck(envConfigProperties);
            configurationItemValueCheck(envConfigProperties);
        } catch (IOException e) {
            throw new MojoExecutionException("\n无法解析环境配置文件");
        }
    }

    /**
     * 校验properties文件 key 和 value 是否首尾包含空格
     *
     * @param envConfigProperties
     * @throws MojoExecutionException
     */
    private void configurationItemValueCheck(OrderedProperties[] envConfigProperties) throws MojoExecutionException {

        String tmpValue;
        for (OrderedProperties property : envConfigProperties) {
            Set<String> names = property.stringPropertyNames();
            for (String name : names) {
                if (name.length() != name.trim().length()) {
                    throw new MojoExecutionException(MessageFormat.format("\n属性 {0} 名称首尾包含空格", name));
                }
                tmpValue = property.getProperty(name);
                if (!StringUtils.isBlank(tmpValue)) {
                    if (tmpValue.length() != tmpValue.trim().length()) {
                        throw new MojoExecutionException(MessageFormat.format("\n属性 {0} 值 \"{1}\" 首尾包含空格", name, tmpValue));
                    }
                }
            }
        }
    }

    /**
     * 有效配置项检查
     *
     * @param envConfigProperties
     * @throws MojoExecutionException
     */
    private void configurationItemCheck(OrderedProperties[] envConfigProperties) throws MojoExecutionException {

        String[] referencePropertyNames = envConfigProperties[0].stringPropertyNames().toArray(new String[]{});
        String[][] comparingProperties = new String[envConfigProperties.length - 1][];
        for (int i = 1; i < envConfigProperties.length; i++) {
            comparingProperties[i - 1] = envConfigProperties[i].stringPropertyNames().toArray(new String[]{});
        }

        int total = envConfigProperties.length;
        int propertyIndex = 0;
        for (String referencePropertyName : referencePropertyNames) {
            for (int i = 1; i < total; i++) {
                if (!comparingProperties[i - 1][propertyIndex].equals(referencePropertyName)) {
                    throw new MojoExecutionException(MessageFormat.format("\n环境配置文件配置项 {0} 不一致(缺少配置或配置所在行不一致)", referencePropertyName));
                }
            }
            propertyIndex++;
        }
    }

    /**
     * 有效配置项数量检查
     *
     * @param envConfigProperties
     * @throws MojoExecutionException
     */
    private void configurationItemNumberCheck(OrderedProperties[] envConfigProperties) throws MojoExecutionException {
        int referenceConfigurationItemNumber = envConfigProperties[0].size();
        int total = envConfigProperties.length;
        for (int i = 1; i < total; i++) {
            if (referenceConfigurationItemNumber != envConfigProperties[i].size()) {
                throw new MojoExecutionException("\n环境配置文件有效配置项数量不一致");
            }
        }
    }

    /**
     * 文件实际行数检查
     *
     * @param files
     * @throws MojoExecutionException
     */
    private void fileLineNumberCheck(File[] files) throws MojoExecutionException {
        int referenceLineNumber = getFileLineNumber(files[0]);
        int total = files.length;
        for (int i = 1; i < total; i++) {
            if (referenceLineNumber != getFileLineNumber(files[i])) {
                throw new MojoExecutionException(MessageFormat.format("\n环境配置文件 {0} 和 {1} 配置行数不一致", files[0].getName(), files[i].getName()));
            }
        }
    }

    /**
     * @param file
     * @return 文件行数
     * @throws IOException
     */
    private int getFileLineNumber(File file) {
        LineNumberReader lnr = null;
        try {
            lnr = new LineNumberReader(new FileReader(file));
            lnr.skip(Long.MAX_VALUE);
            int lineNo = lnr.getLineNumber() + 1;
            lnr.close();
            return lineNo;
        } catch (IOException e) {
        } finally {
            if (lnr != null) {
                try {
                    lnr.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return 0;
    }

    /**
     * Gets project.
     *
     * @return the project
     */
    public MavenProject getProject() {
        return project;
    }

    /**
     * Sets project.
     *
     * @param project the project
     */
    public void setProject(MavenProject project) {
        this.project = project;
    }

    /**
     * Gets env config file name.
     *
     * @return the env config file name
     */
    public String getEnvConfigFileName() {
        return envConfigFileName;
    }

    /**
     * Sets env config file name.
     *
     * @param envConfigFileName the env config file name
     */
    public void setEnvConfigFileName(String envConfigFileName) {
        this.envConfigFileName = envConfigFileName;
    }
}











