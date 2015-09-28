(ns buergeramt-sniper.crawler
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojurewerkz.urly.core :as urly]))

;;;; Conventions
;;;; load-*    String -> Enlive DOM

(defrecord Crawler [base-url])

(defn resolve-href
  "Given a node, resolve its :href attr if it is relative, return transformed node"
  [base-url node]
  (update-in node [:attrs :href] #(try (urly/resolve base-url %)
                                       (catch Exception _ nil))))

(defn- parse-html
  "Parse page body and resolve relative hrefs, return DOM"
  [url body]
  (-> (html/html-snippet body)
      (html/at [:a] (partial resolve-href url))))

(defn load-page
  "Load a page and parse it, return DOM or nil"
  [^String href]
  (log/debug "Loading" href)
  (let [{:keys [status body error]} @(http/get href)]
    (log/spy [status error])
    (if error
      (log/error "Error:" error)
      (if (not= 200 status)
        (log/error "Status:" status)
        (parse-html href body)))))


(defn load-root
  [^Crawler crawler]
  (log/info "Loading root page")
  (load-page (:base-url crawler)))

(defn load-calendar-page
  [^String href]
  (log/info "Loading calendar page")
  (load-page href))
