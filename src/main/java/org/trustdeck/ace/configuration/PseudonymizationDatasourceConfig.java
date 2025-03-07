/*
 * ACE - Advanced Confidentiality Engine
 * Copyright 2022-2024 Armin Müller & Eric Wündisch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.trustdeck.ace.configuration;

import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The data source configuration for the pseudonymization service.
 * 
 * @author Armin Müller & Eric Wündisch
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@Component
public class PseudonymizationDatasourceConfig {

    /**
     * Create new pseudonymization data source properties.
     *
     * @return the data source properties
     */
    @Bean(name = "pseudonymizationDataSourceProperties")
    @ConfigurationProperties(prefix = "app.datasource.ace")
    public DataSourceProperties pseudonymizationDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Create a new Hikari data source for the pseudonymization service.
     *
     * @param dataSourceProperties the properties for the data source
     * @return the Hikari data source
     */
    @Bean(name = "pseudonymizationDataSource")
    @ConfigurationProperties(prefix = "app.datasource.ace.configuration")
    public HikariDataSource pseudonymizationDataSource(@Qualifier("pseudonymizationDataSourceProperties") DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /**
     * Create a new data source connection provider.
     *
     * @param dataSource the data source
     * @return the data source connection provider
     */
    @Bean
    public DataSourceConnectionProvider pseudonymizationConnectionProvider(@Qualifier("pseudonymizationDataSource") HikariDataSource dataSource) {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource));
    }

    /**
     * Create a new domain-specific language (DSL) context.
     *
     * @param dataSource the data source
     * @return the dsl context
     */
    @Bean(name = "pseudonymizationDsl")
    public DSLContext pseudonymizationDsl(@Qualifier("pseudonymizationDataSource") HikariDataSource dataSource) {
        return new DefaultDSLContext(pseudonymizationConfiguration(dataSource));
    }

    /**
     * Create a new default configuration for the pseudonymization service.
     *
     * @param dataSource the data source
     * @return the default configuration
     */
    public DefaultConfiguration pseudonymizationConfiguration(@Qualifier("pseudonymizationDataSource") HikariDataSource dataSource) {
        DefaultConfiguration config = new DefaultConfiguration();
        config.set(pseudonymizationConnectionProvider(dataSource));
        config.set(SQLDialect.POSTGRES);
        return config;
    }
}
