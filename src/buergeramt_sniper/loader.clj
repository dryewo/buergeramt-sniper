(ns buergeramt-sniper.loader
  (:require [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [clojurewerkz.urly.core :as urly]
            [schema.core :as s]))

(s/defschema Dom
  "Enlive DOM schema"
  (s/either s/Str
            {:type s/Any
             :data s/Any}
            {:tag     s/Any
             :attrs   s/Any
             :content s/Any}))

(s/defrecord Loader
  [base-url :- s/Str])

(s/defn resolve-href :- Dom
  "Given a node, resolve its :href attr if it is relative, return transformed node"
  [base-url :- s/Str
   node :- Dom]
  (update-in node [:attrs :href] #(try (urly/resolve base-url %)
                                       (catch Exception _ nil))))

(s/defn parse-html :- [Dom]
  "Parse page body and resolve relative hrefs, return DOM"
  [url :- s/Str
   body :- s/Str]
  (-> (html/html-snippet body)
      (html/at [:a] (partial resolve-href url))))

(s/defn load-page :- [Dom]
  "Load a page and parse it, return DOM or nil"
  [href :- s/Str]
  (log/debug "Loading" href)
  (let [{:keys [status body error]} @(http/get href)]
    (log/trace [status error])
    (if error
      (log/error "Error:" error)
      (if (not= 200 status)
        (log/error "Status:" status)
        (parse-html href body)))))
