package io.metersphere.api.dto.definition.request;

import io.metersphere.api.dto.definition.request.variable.ScenarioVariable;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.commons.utils.ScriptEngineUtils;
import lombok.Data;
import org.apache.jmeter.config.Arguments;

import java.util.List;
import java.util.Map;

@Data
public class ParameterConfig {
    /**
     * 环境配置
     */
    private Map<String, EnvironmentConfig> config;
    /**
     * 公共场景参数
     */
    private List<ScenarioVariable> variables;
    /**
     * 公共Cookie
     */
    private boolean enableCookieShare;

    /**
     * 是否是导入/导出操作
     */
    private boolean isOperating;
    /**
     * 项目ID，支持单接口执行
     */
    private String projectId;

    public boolean isEffective(String projectId) {
        if (this.config != null && this.config.get(projectId) != null) {
            return true;
        }
        return false;
    }

    static public Arguments valueSupposeMock(Arguments arguments) {
        for(int i = 0; i < arguments.getArguments().size(); ++i) {
            String argValue = arguments.getArgument(i).getValue();
            arguments.getArgument(i).setValue(ScriptEngineUtils.calculate(argValue));
        }
        return arguments;
    }

}