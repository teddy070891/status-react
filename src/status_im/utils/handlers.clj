(ns status-im.utils.handlers)

(defmacro handlers->
  "Help thread multiple handler functions.
   All functions are expected to accept [db event] as parameters.
   If one handler returns a modified db it will be used as parameters for subsequent handlers."
  [& forms]
  (let [db     (gensym "db")
        event  (gensym "event")
        new-db (gensym "new-db")]
    `(fn [~db ~event]
       (let [~@(interleave (repeat db)
                           (map (fn [form]
                                  `(let [~new-db (~form ~db ~event)]
                                     (if (map? ~new-db)
                                       ~new-db
                                       ~db))) forms))]
         ~db))))

(defmacro fx->*
  {:added "1.0"}
  [fx cofx & forms]
  (if forms
    (let [form (first forms)
          temp-cofx (gensym 'temp-cofx)]
      `(let [~temp-cofx (update-db ~cofx ~fx)
             fx# (safe-merge ~fx ~(with-meta `(~(first form) ~temp-cofx ~@(next form)) (meta form)))]
         (fx->* fx# ~temp-cofx ~@(next forms))))
    fx))

(defmacro fx->
  {:added "1.0"}
  "Takes a map of effects and functions applying effects and returns a form that ensures
  that updates to db are passed from function to function within the cofx :db key and
  that only a :merging-fx-with-common-keys effect is returned if some functions are trying
  to produce the same effects (excepted :db effect)"
  [fx & forms]
  (let [form (first forms)
        temp-cofx (gensym 'temp-cofx)]
    `(let [~temp-cofx (update-db ~'cofx ~fx)
           fx# (safe-merge ~fx ~(with-meta `(~(first form) ~temp-cofx ~@(next form)) (meta form)))]
       (fx->* fx# ~temp-cofx ~@(next forms)))))

(comment (defn fn1 [{:keys [db]}]
           {:db (assoc db :a 0)
            :a "1"})

         (defn fn2 [{:keys [db]} a]
           {:db (update db :a + a)
            })

         (defn fn3 [{:keys [db]} a]
           {:db (update db :a + a)})

         (let [a    1
               b    2
               cofx {:db {}}]
           (fx-> {:db {:hello 2}}
                 (fn1)
                 (fn2 a)
                 (fn3 b))))
