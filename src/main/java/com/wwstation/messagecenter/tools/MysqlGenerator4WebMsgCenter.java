package com.wwstation.messagecenter.tools;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.InjectionConfig;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mybatis-plus 代码生成器
 *
 * @author william
 * @description
 * @Date: 2020-12-11 15:43
 */
public class MysqlGenerator4WebMsgCenter {
    private static final String AUTHOR_NAME="william";
    //如果在一级目录，此处为空字符串
    private static final String MODULE_NAME="";

    private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    private static final String USER_NAME = "root";
    private static final String PASSWORD = "williamworkstation";
    private static final String DB_URL = "jdbc:mysql://192.168.2.100:3306/web_message_center?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    private static final String BASE_PACKAGE = "com.wwstation.messagecenter";


    public static void main(String[] args) {
        String[] models = {MODULE_NAME};//项目包名
        for (String model : models) {
            shell(model, "basic_config");//日志的收集统一放进了resource中做(因为使用了APO织入业务逻辑，有强依赖)
            shell(model, "consumer_config");
            shell(model, "failed_message");
            shell(model,"dead_message");
        }
    }

    private static void shell(String model, String table) {
        File file = new File(model);
        String path = file.getAbsolutePath();
        AutoGenerator mpg = new AutoGenerator();
        // 全局配置
        GlobalConfig gc = new GlobalConfig();
        gc.setOutputDir(path + "/src/main/java");
        gc.setFileOverride(true);
        gc.setActiveRecord(true);
        gc.setOpen(false);
        gc.setEnableCache(false);// XML 二级缓存
        gc.setBaseResultMap(true);// XML ResultMap
        gc.setBaseColumnList(true);// XML columList
        gc.setSwagger2(true);
        gc.setAuthor(AUTHOR_NAME);

        // 自定义文件命名，注意 %s 会自动填充表实体属性！
        gc.setMapperName("%sMapper");
        gc.setXmlName("%sMapper");
        gc.setServiceName("MP%sService");//加一个MP（mybatis-plus）标识，尽量不要与其它服务重名
        gc.setServiceImplName("MP%sServiceImpl");
        gc.setControllerName("%sController");
        mpg.setGlobalConfig(gc);

        // 数据源配置
        DataSourceConfig dsc = new DataSourceConfig();
        dsc.setDbType(DbType.MYSQL);
        dsc.setDriverName(DRIVER_CLASS_NAME);
        dsc.setUsername(USER_NAME);
        dsc.setPassword(PASSWORD);
        dsc.setUrl(DB_URL);
        mpg.setDataSource(dsc);

        // 策略配置
        StrategyConfig strategy = new StrategyConfig();
        strategy.setNaming(NamingStrategy.underline_to_camel);// 表名生成策略
        strategy.setInclude(new String[]{table}); // 需要生成的表
        strategy.setEntityLombokModel(true); // 是否为lombok模型
        strategy.setRestControllerStyle(true);
        mpg.setStrategy(strategy);
        String pack = "";
        // 包配置
        PackageConfig pc = new PackageConfig();
        pc.setParent(BASE_PACKAGE);
        pc.setEntity("model.po");
        pc.setController("");
        pc.setMapper("mapper");
        pc.setService("service");
        pc.setServiceImpl("service.impl");
        mpg.setPackageInfo(pc);

        // 注入自定义配置，可以在 VM 中使用 cfg.abc 【可无】
        InjectionConfig cfg = new InjectionConfig() {
            @Override
            public void initMap() {
                Map<String, Object> map = new HashMap<>();
                map.put("abc", this.getConfig().getGlobalConfig().getAuthor() + "-mp");
                this.setMap(map);
            }
        };
        if (true) {//path.contains("service")) {
            List<FileOutConfig> focList = new ArrayList<>();
            // 调整 xml 生成目录演示
            //velocity 引擎使用.vm
            focList.add(new FileOutConfig("/templates/mapper.xml.vm") {
                @Override
                public String outputFile(TableInfo tableInfo) {
                    return path + "/src/main/resources/" +
                        pc.getParent().replace(".", "/") + "/mapper/"
                        + tableInfo.getEntityName() + "Mapper" + StringPool.DOT_XML;
                }
            });
            cfg.setFileOutConfigList(focList);
        }
        mpg.setCfg(cfg);
        //此处配置生成策略
        TemplateConfig tc = new TemplateConfig();
        //不生成controller
        tc.setController(null);


//        if (model.endsWith("model")) {
//            tc.setController(null);
//            tc.setService(null);
//            tc.setServiceImpl(null);
//            tc.setMapper(null);
//        } else if (model.endsWith("service")) {
//            tc.setController(null);
//            tc.setEntity(null);
//        } else if (model.endsWith("api")) {
//            tc.setMapper(null);
//            tc.setService(null);
//            tc.setServiceImpl(null);
//            tc.setEntity(null);
//        }
        mpg.setTemplate(tc.setXml(null));


        mpg.execute();

        // 打印注入设置【可无】
        System.err.println(mpg.getCfg().getMap().get("abc"));
    }

}
