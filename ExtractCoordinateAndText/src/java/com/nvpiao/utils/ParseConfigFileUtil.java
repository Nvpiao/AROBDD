/*  Copyright (c) 2020 Ming Liu <Mliu54@sheffield.ac.uk>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nvpiao.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration file reader
 *
 * @author Ming Liu
 */
public class ParseConfigFileUtil {
    public static Properties parse(String path) {
        Properties prop = new Properties();
        try {
            prop.load(new FileInputStream(new File(path)));
        } catch (IOException e) {
            try {
                prop.load(ParseConfigFileUtil.class.getClassLoader().getResourceAsStream(path));
            } catch (IOException ex) {
                e.printStackTrace();
                throw new RuntimeException("Configuration file 'config' does not exist." + e);
            }
        }
        return prop;
    }
}
