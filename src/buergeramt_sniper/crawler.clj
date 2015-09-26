(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]))

(defrecord Crawler [base-url])

(defn load-page
  "Returns page body (as String) or nil"
  [^String href]
  (log/debug "Loading" href)
  (let [{:keys [status body error]} @(http/get href)]
    (log/spy [status error])
    (if error
      (log/error "Error:" error)
      (if (not= 200 status)
        (log/error "Status:" status)
        body))))


(defn load-root
  "Returns root page body (as String) or nil"
  [^Crawler crawler]
  (log/info "Loading root page")
  (load-page (:base-url crawler)))

(defn load-calendar-page
  [^String href]
  (log/info "Loading calendar page")
  (load-page href))
