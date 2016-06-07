/*
 * Copyright 2009-2016 Weibo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.weibo.api.motan.protocol.yar;

import java.lang.reflect.Method;

import org.apache.commons.lang3.StringUtils;

import com.weibo.api.motan.exception.MotanBizException;
import com.weibo.api.motan.exception.MotanServiceException;
import com.weibo.api.motan.protocol.yar.annotation.YarConfig;
import com.weibo.api.motan.rpc.DefaultRequest;
import com.weibo.api.motan.rpc.DefaultResponse;
import com.weibo.api.motan.rpc.Request;
import com.weibo.api.motan.rpc.Response;
import com.weibo.api.motan.rpc.URL;
import com.weibo.api.motan.util.ReflectUtil;
import com.weibo.yar.YarRequest;
import com.weibo.yar.YarResponse;

public class YarProtocolUtil {
    // 如果接口类有
    public static String getYarPath(Class<?> interfaceClazz, URL url) {
        if (interfaceClazz != null) {
            YarConfig config = interfaceClazz.getAnnotation(YarConfig.class);
            if (config != null && StringUtils.isNotBlank(config.path())) {
                return config.path();
            }
        }
        // 默认使用/group/urlpath
        return "/" + url.getGroup() + "/" + url.getPath();
    }

    /**
     * 转换yar请求为motan rpc请求。 由于php类型不敏感，故转换请求时只判断方法名和参数个数是否相等。相等时尝试转换为对应类型。
     * 
     * @param yarRequest
     * @param interfaceClass
     * @return
     */
    public static Request convert(YarRequest yarRequest, Class<?> interfaceClass) {
        DefaultRequest request = new DefaultRequest();
        request.setInterfaceName(interfaceClass.getName());
        request.setMethodName(yarRequest.getMethodName());
        request.setRequestId(yarRequest.getId());
        addArguments(request, interfaceClass, yarRequest.getMethodName(), yarRequest.getParameters());
        return request;
    }

    public static YarRequest convert(Request request, Class<?> interfaceClass, String packagerName) {
        YarRequest yarRequest = new YarRequest();
        yarRequest.setId(request.getRequestId());
        yarRequest.setMethodName(request.getMethodName());
        yarRequest.setPackagerName(packagerName);
        yarRequest.setParameters(request.getArguments());
        return yarRequest;
    }

    public static Response convert(YarResponse yarResponse) {
        DefaultResponse response = new DefaultResponse();
        response.setRequestId(yarResponse.getId());
        response.setValue(yarResponse.getRet());
        if (StringUtils.isNotBlank(yarResponse.getError())) {
            response.setException(new MotanBizException(yarResponse.getError()));
        }

        return response;
    }

    public static YarResponse convert(Response response, String packagerName) {
        YarResponse yarResponse = new YarResponse();
        yarResponse.setId(response.getRequestId());
        yarResponse.setPackagerName(packagerName);
        yarResponse.setRet(response.getValue());
        if (response.getException() != null) {
            yarResponse.setError(response.getException().getMessage());
        }

        return yarResponse;
    }

    /**
     * 给Request添加请求参数相关信息。 由于php类型不敏感，所以只对方法名和参数个数做匹配，然后对参数做兼容处理
     * 
     * @param interfaceClass
     * @param methodName
     * @param arguments
     * @return
     */
    private static void addArguments(DefaultRequest request, Class<?> interfaceClass, String methodName, Object[] arguments) {
        Method targetMethod = null;
        // TODO 是否需要缓存
        Method[] methods = interfaceClass.getDeclaredMethods();
        for (Method m : methods) {
            if (m.getName().equalsIgnoreCase(methodName) && m.getParameterTypes().length == arguments.length) {
                targetMethod = m;
                break;
            }
        }
        if (targetMethod == null) {
            throw new MotanServiceException("cann't find request method. method name " + methodName);
        }

        request.setParamtersDesc(ReflectUtil.getMethodParamDesc(targetMethod));

        if (arguments != null && arguments.length > 0) {
            Class<?>[] argumentClazz = targetMethod.getParameterTypes();
            request.setArguments(adaptParams(arguments, argumentClazz));
        }


    }

    public static YarResponse buildDefaultErrorResponse(String errMsg, String packagerName) {
        YarResponse yarResponse = new YarResponse();
        yarResponse.setPackagerName(packagerName);
        yarResponse.setError(errMsg);
        yarResponse.setStatus("500");
        return yarResponse;
    }


    // 参数适配为java对应类型
    private static Object[] adaptParams(Object[] arguments, Class<?>[] argumentClazz) {

        for (int i = 0; i < argumentClazz.length; i++) {
            if (("java.lang.Double".equals(arguments[i].getClass().getName()) && "float".equals(argumentClazz[i].getName()) || "java.lang.Float"
                    .equals(argumentClazz[i].getName()))) {
                arguments[i] = ((Double) arguments[i]).floatValue();
            } else if ("java.lang.Long".equals(arguments[i].getClass().getName())
                    && ("int".equals(argumentClazz[i].getName()) || "java.lang.Integer".equals(argumentClazz[i].getName()))) {
                arguments[i] = ((Long) arguments[i]).intValue();
            }
        }
        return arguments;
    }


}