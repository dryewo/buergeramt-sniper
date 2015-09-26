(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]))

(defrecord RootPage [appointment-link])

(defn parse-root
  "Parses html content and returns RootPage map"
  [data]
  (log/spy ((juxt class count) data))
  (let [dom (html/html-snippet data)]
    (when-let [app-link (-> dom
                            (html/select [:div.zmstermin-multi :a.btn])
                            first
                            log/spy)]
      (log/spy (->RootPage (get-in app-link [:attrs :href]))))))
