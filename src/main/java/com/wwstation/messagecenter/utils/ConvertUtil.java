package com.wwstation.messagecenter.utils;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 参数类型转换工具类
 */
@Slf4j
public class ConvertUtil {

    /**
     * 参数转换,浅拷贝
     *
     * @param source 原始对象
     * @param clz    转换后的对象类型
     * @return
     */
    public final static <S, T> T convert(S source, Class<T> clz) {
        T target = null;
        try {
            target = clz.newInstance();
            BeanUtil.copyProperties(source, target);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(source.toString(), target.toString());
        }
        return target;
    }

    /**
     * 参数转换,可以转换list中的list或map，集合对象只能copy一层，浅拷贝
     *
     * @param sourceList 原始对象集合
     * @param clz        转换后的对象类型
     * @return
     */
    public static <S, T> List<T> convertList(List<S> sourceList, Class<T> clz) {
        return sourceList.stream().map(e -> {
            return convert(e, clz);
        }).collect(Collectors.toList());
    }

    /**
     * 将响应转为jsonobject
     *
     * @param data
     * @return
     */
    public static JSONObject getJsonObjectFromResponse(Object data) {
        if (!Map.class.isAssignableFrom(data.getClass())) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JSONObject object = objectMapper.convertValue(data, JSONObject.class);
        return object;
    }

    public static JSONArray getJsonArrayFromResponse(Object data) {
        if (!List.class.isAssignableFrom(data.getClass())) {
            return null;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JSONArray object = objectMapper.convertValue(data, JSONArray.class);
        return object;
    }

    /**
     * 实现对象的深拷贝
     *
     * @param obj
     * @param <T>
     * @return
     */
    public static <T extends Serializable> T deepCopy(T obj) {
        if (!Serializable.class.isAssignableFrom(obj.getClass())) {
            throw new RuntimeException("对象没有继承自Serializable，无法进行深拷贝");
        }
        return SerializationUtils.clone(obj);
    }


}
