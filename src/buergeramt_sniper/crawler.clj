(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]))

(defrecord Crawler [base-url])

(defn load-root [crawler]
  (log/spy crawler)
  (let [{:keys [status headers body error] :as resp} @(http/get (:base-url crawler))]
    (if error
      (println "Failed, exception: " error)
      (println "HTTP GET success: " status (count body) "bytes"))))