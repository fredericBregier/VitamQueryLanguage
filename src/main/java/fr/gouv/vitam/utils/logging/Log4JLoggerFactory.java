/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package fr.gouv.vitam.utils.logging;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Logger factory which creates an
 * <a href="http://logging.apache.org/log4j/1.2/index.html">Apache Log4J</a>
 * logger.
 */
public class Log4JLoggerFactory extends VitamLoggerFactory {

    public Log4JLoggerFactory(VitamLogLevel level) {
		super(level);
		Logger logger = Logger.getRootLogger();
		if (level == null) {
			logger.info("Default level: " + logger.getLevel());
		} else {
			switch (level) {
			case TRACE:
				logger.setLevel(Level.TRACE);
				break;
			case DEBUG:
				logger.setLevel(Level.DEBUG);
				break;
			case INFO:
				logger.setLevel(Level.INFO);
				break;
			case WARN:
				logger.setLevel(Level.WARN);
				break;
			case ERROR:
				logger.setLevel(Level.ERROR);
				break;
			default:
				logger.setLevel(Level.WARN);
				break;
			}
		}
	}

	@Override
    public VitamLogger newInstance(String name) {
        return new Log4JLogger(Logger.getLogger(name));
    }

	@Override
	protected void setDefaultLevel(VitamLogLevel level) {
		if (level == null) {
			return;
		}
		currentLevel = level;
		Logger logger = Logger.getRootLogger();
		switch (level) {
		case TRACE:
			logger.setLevel(Level.TRACE);
			break;
		case DEBUG:
			logger.setLevel(Level.DEBUG);
			break;
		case INFO:
			logger.setLevel(Level.INFO);
			break;
		case WARN:
			logger.setLevel(Level.WARN);
			break;
		case ERROR:
			logger.setLevel(Level.ERROR);
			break;
		default:
			logger.setLevel(Level.WARN);
			break;
		}
	}
}