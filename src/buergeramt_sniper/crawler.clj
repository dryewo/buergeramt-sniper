(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]))

(defrecord Crawler [base-url])

(defn load-root
  "Returns page body (as String) or nil"
  [crawler]
  (log/spy crawler)
  (let [{:keys [status body error]} @(http/get (:base-url crawler))]
    (log/spy [status error])
    (if error
      (log/error "Error:" error)
      (if (not= 200 status)
        (log/error "Status:" status)
        body))))