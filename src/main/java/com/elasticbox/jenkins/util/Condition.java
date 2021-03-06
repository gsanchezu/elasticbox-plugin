/*
 * ElasticBox Confidential
 * Copyright (c) 2015 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.util;

import org.apache.commons.lang.time.StopWatch;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class Condition {

    private static final Logger logger = Logger.getLogger(Condition.class.getName());

    public abstract boolean satisfied();

    public synchronized void waitUntilSatisfied(long timeoutSeconds) {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        while (!satisfied() && stopWatch.getTime() < TimeUnit.SECONDS.toMillis(timeoutSeconds)) {
            try {
                wait(1000);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, "Thread Interrupted ", ex);
            }
        }
    }

}
