/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.jdbc.datasource.init;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.util.Assert;

/**
 * Used to populate a database during initialization.
 *
 * @author Dave Syer
 * @since 3.0
 * @see DatabasePopulator
 */
public class DataSourceInitializer implements InitializingBean, DisposableBean {

	private DataSource dataSource;

	private DatabasePopulator databasePopulator;

	private DatabasePopulator databaseCleaner;

	private boolean enabled = true;

	/**
	 * The {@link DataSource} to populate when this component is initialized.
	 * Mandatory with no default.
	 * @param dataSource the DataSource
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/**
	 * The {@link DatabasePopulator} to use to populate the data source.
	 * Mandatory with no default.
	 * @param databasePopulator the database populator to use.
	 */
	public void setDatabasePopulator(DatabasePopulator databasePopulator) {
		this.databasePopulator = databasePopulator;
	}

	/**
	 * Set a script execution to be run in the bean destruction callback, cleaning up the database and leaving it in 
	 * a known state for others.
	 * 
	 * @param databaseCleaner the database script executor to run on destroy
	 */
	public void setDatabaseCleaner(DatabasePopulator databaseCleaner) {
		this.databaseCleaner = databaseCleaner;
	}

	/**
	 * Flag to explicitly enable or disable the database populator.
	 * @param enabled true if the database populator will be called on startup
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Use the populator to set up data in the data source.
	 */
	public void afterPropertiesSet() throws Exception {
		if (this.databasePopulator != null) {
			execute(this.databasePopulator);
		}
	}

	/**
	 * Use the populator to clean up data in the data source.
	 */
	public void destroy() throws Exception {
		if (this.databaseCleaner != null) {
			execute(this.databaseCleaner);
		}
	}

	private void execute(DatabasePopulator populator) throws Exception {
		if (this.enabled) {
			Assert.state(this.dataSource != null, "DataSource must be provided");
			Assert.state(populator != null, "DatabasePopulator must be provided");
			try {
				Connection connection = this.dataSource.getConnection();
				try {
					populator.populate(connection);
				} finally {
					try {
						connection.close();
					} catch (SQLException ex) {
						// ignore
					}
				}
			} catch (Exception ex) {
				throw new DataAccessResourceFailureException("Failed to execute database script", ex);
			}
		}
	}

}
