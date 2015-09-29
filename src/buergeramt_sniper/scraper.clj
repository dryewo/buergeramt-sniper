(ns buergeramt-sniper.scraper
  (:require [clojure.tools.logging :as log]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [schema.core :as s]))

(s/defschema Dom
  "Enlive DOM schema"
  (s/either s/Str
            {:type s/Any
             :data s/Any}
            {:tag     s/Any
             :attrs   s/Any
             :content s/Any}))

(s/defrecord RootPage
  [appointment-href :- s/Str
   title :- s/Str])

(s/defrecord Time
  [time :- s/Str
   place :- s/Str
   href :- s/Str])

(s/defrecord Day
  [times :- [Time]])

(s/defrecord Month
  [name :- s/Str
   closed-dates :- [s/Any]
   open-dates :- [s/Any]
   prev-href :- (s/maybe s/Str)
   next-href :- (s/maybe s/Str)])

(s/defrecord OpenDate
  [text :- s/Str
   href :- s/Str])

(s/defrecord ClosedDate
  [text :- s/Str])

(s/defrecord CalendarPage
  [months :- [Month]])

(s/defn ^:always-val1idate parse-root-page :- RootPage
  "Parse dom tree and return RootPage map"
  [dom :- [Dom]]
  (log/info "Parsing root page")
  (let [[appointment-a] (-> dom (html/select [:div.zmstermin-multi :a.btn]))
        [title-h1] (-> dom (html/select [:div.article :div.header :h1.title]))]
    (log/spy (strict-map->RootPage {:appointment-href (-> appointment-a :attrs :href)
                                    :title            (-> title-h1 html/text str/trim)}))))

(s/defn ^:always-validate parse-closed-date :- ClosedDate
  "Parse date cell and return its text"
  [dom :- Dom]
  (strict-map->ClosedDate {:text (-> dom html/text str/trim)}))

(s/defn ^:always-validate parse-open-date :- OpenDate
  "Parse date cell and return its text and href"
  [dom]
  (strict-map->OpenDate {:text (-> dom html/text str/trim)
                         :href (-> dom
                                   (html/select [:a])
                                   first
                                   :attrs
                                   :href)}))

(s/defn ^:always-validate parse-month :- Month
  "Parse month table and return Month map"
  [dom :- Dom]
  (let [[month-name] (html/select dom [:th.month])
        [prev-link] (html/select dom [:th.prev :a])
        [next-link] (html/select dom [:th.next :a])
        open-dates (html/select dom [:td.buchbar])
        closed-dates (html/select dom [:td.nichtbuchbar])]
    (strict-map->Month {:name         (some-> month-name html/text str/trim)
                        :closed-dates (map parse-closed-date closed-dates)
                        :open-dates   (map parse-open-date open-dates)
                        :prev-href    (some-> prev-link :attrs :href)
                        :next-href    (some-> next-link :attrs :href)})))

(s/defn ^:always-validate parse-calendar-page :- CalendarPage
  "Parse dom tree and return CalendarPage map"
  [dom :- [Dom]]
  (log/info "Parsing calendar page")
  (when-let [months (-> dom (html/select [:div.calendar-month-table]) seq)]
    (strict-map->CalendarPage {:months (map parse-month months)})))

(s/defn ^:always-validate parse-timetable-record :- Time
  [dom :- Dom]
  (let [[th] (html/select dom [:th])
        [td-a] (html/select dom [:td :a])]
    (strict-map->Time {:time  (some-> th html/text str/trim)
                       :place (some-> td-a html/text str/trim)
                       :href  (some-> td-a :attrs :href)})))

(s/defn ^:always-validate fix-omitted-times :- [Time]
  "If :time for record is empty, take it from the previous record"
  [records :- [Time]]
  (->>
    (reduce
      (fn [[last-time acc] r]
        (if (str/blank? (:time r))
          [last-time (conj acc (assoc r :time last-time))]
          [(:time r) (conj acc r)]))
      ["" []]
      records)
    second))

(s/defn ^:always-validate parse-daytimes-page :- Day
  [dom :- [Dom]]
  (log/info "Parsing daytimes page")
  (when-let [timetable-records (some-> dom (html/select [:div.timetable :tr]) seq)]
    (strict-map->Day {:times (some->> timetable-records
                                      (map parse-timetable-record)
                                      (fix-omitted-times))})))
