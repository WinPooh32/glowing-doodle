"use strict";

const util = require('util');
const mysql = require('mysql');
const http = require('http');

const SERVER_PORT = 3384;
const SERVER_HOST = '192.168.1.140';

const MYSQL_PORT = 1194
const MYSQL_HOST = '192.168.1.140'

const PULL_ALL = 0;
const PULL_DEVICE = 1;

const httpServer = http.createServer().listen(SERVER_PORT, SERVER_HOST);

let io = null;
let clients = null;
let db = null;

function newDBConnection() {
    return mysql.createConnection({
        host: MYSQL_HOST,
        port: MYSQL_PORT,
        user: 'superstars',
        password: 'MzI4ZjNmZjEyNTNiYjFhOTcwODMzZjM0',
        database: 'SUPERSTARS'
    });
}

function start() {
    console.log("Waiting for a new connection.");

    db = newDBConnection();
    io = require('socket.io')({transports: ['websocket']});

    io.listen(httpServer);

    clients = {};

    db.connect((err) => {
        if (err) {
            console.error('error: ' + err.stack);
            throw err;
        }

        io.on('connection', function (socket) {
            let address = socket.handshake.address + ": ";            
            console.log(address + 'Client connected.');          

            addClient(clients, socket);

            //===========================================================
            //Event handlers
            //===========================================================
            socket.on('pull_all', pullAll);
            socket.on('pull_device_items', pullDeviceItems);
            socket.on('pull_item', pullItemByHash);

            socket.on('push_device', pushDevice);
            socket.on('push_updates', pushUpdates);

            socket.on('disconnect', () => {
                removeClient(clients, socket);

                console.log(address + 'Client disconnected.\nClients left: %s',
                        Object.keys(clients).length
                        );
            });
            //===========================================================

            function pushDevice(android_id) {
                let sql = mysql.format("call add_device(?);", [android_id]);

                db.query(sql, (error, results, fields) => {
                    if (!error) {
                        socket.emit('CALLBACK_push_device', null);
                    } else {
                        if (error.code === 'ER_DUP_ENTRY') {
                            socket.emit('CALLBACK_push_device', null);
                        } else {
                            logSqlError(error);
                            socket.emit('CALLBACK_push_device', error.code);
                        }
                    }
                });
            }

            function pushUpdates(updates) {
                let items = updates.items;
                let relations = updates.relations;
                let count = items.length;

                let donePush = (count) => {
                    if (count <= 0) {
                        socket.emit("CALLBACK_push_updates");
                        socket.broadcast.emit("SERVER_UPDATE");
                        console.log("SERVER_UPDATE");
                    }
                };

                //FIXME debug
                console.log(address + "push_updates!");
//                console.log(address + updates);

                for (let i = 0; i < items.length; ++i) {
                    let item = items[i];
                    let rel = findParent(relations, item.android_id);

                    if (rel) {
                        item.parent_hash = rel.item_hash;
                    }
                    
                    if(item.delete){
                        deleteItem(item.hash_name, item.android_id, (error) => {
                            donePush(--count);
                        });
                    }else{
                        pushItem(item, (error) => {
                            donePush(--count);
                        });
                    }
                }
            }

            function pullItems(pullMethod, opts) {
                let items = [];

                let event;

                let completelyDone = (tags_counter) => {

                    if (tags_counter === 0) {

                        items = arrayItemsUnique(items);
                        console.log(items);
                        
                        
                        let result = {
                            'items': items,
                            'relations': arrayRelsUnique(makeRelations(items))
                        };
                                
                        //FIXME debug
//                        console.log(address + result);

                        socket.emit(event, null, result);
                    }
                };

                let onDone = (counter) => {
                    if (counter === 0) {
                        //emit
                        let tags_counter = items.length;

                        if (tags_counter === 0) {
                            completelyDone(0);
                        }

                        for (let i = 0; i < items.length; ++i) {
                            let item = items[i];

                            getItemTags(item.id, (tags, error) => {
                                item.tags = tags;
                                completelyDone(--tags_counter);
                            });
                        }
                    }
                };

                let callBack = (results, error) => {
                    //FIXME debug
//                    console.log(address + "callBack()");

                    if (!error) {
                        let rows = results[0];

                        let counter = rows.length;

                        if (counter === 0) {
                            completelyDone(0);
                        }

                        for (let i = 0; i < rows.length; ++i) {

                            if (rows[i].parent_item === null) {
                                getItemSubscribers(rows[i].id, (subscribers) => {
                                    items = items.concat(subscribers[0]);
                                    onDone(--counter);
                                });
                            } else {
                                //add parents
                                getItemSubscribers(rows[i].parent_item, (subscribers) => {
                                    items = items.concat(subscribers[0]);
                                    onDone(--counter);
                                });
                            }
                        }
                    } else {
                        completelyDone(0);
                    }
                };

                switch (pullMethod) {
                    case PULL_ALL:
                        event = "CALLBACK_pull_all";
                        getAllItems(opts.page, callBack);
                        break;

                    case PULL_DEVICE:
                        event = "CALLBACK_pull_device_items";
                        getItemsByDevice(opts.device, callBack);
                        break;

                    default:
                        console.log(address + "Unknown pull method: ", pullMethod);
                }
            }

            function pullDeviceItems(device) {
                //FIXME debug
                console.log(address + "pull_device_items");
                pullItems(PULL_DEVICE, {device: device});
            }

            function pullAll(page) {
                //FIXME debug
                console.log(address + "pull_all_items");
                pullItems(PULL_ALL, {page: page});
            }
            
            function pullItemByHash(item_hash){
                
                console.log(address + "\n\n\nPULL ITEM!!!\n");
                
                let sql = mysql.format(
                    "CALL select_item_by_hash(?)",
                    [item_hash]
                );

                db.query(sql, (error, results, fields) => {
                    let onError = ()=>{
                         socket.emit("CALLBACK_pull_item", null);
                    };
                    
                    if (!error) {
                        if(results.length > 0){
                            let item = results[0][0];
                            
                            if(typeof(item) === 'undefined'){
                                onError();
                                return;
                            }
                            
                            getItemTags(item.id, (tags, error)=>{
                                item['tags'] = tags
                                //FIXME debug
//                                console.log(address + item);
                                socket.emit("CALLBACK_pull_item", item);
                            });
                        }else{
                            console.log(address + "%s not found", item_hash);
                            onError();
                        }
                    } else {
                        logSqlError(error);
                        onError();
                    }
                });
            }

//            function pullItem(device, item_hash) {
//                let sql = mysql.format(
//                        "SELECT * FROM Items WHERE ?? <=> ? LIMIT 1;",
//                        ["hash_name", item_hash]
//                        );
//
//                db.query(sql, (error, results, fields) => {
//                    if (!error) {
//                        //TODO emit
//                    } else {
//                        logSqlError(error);
//                    }
//                });
//            }

//            function pullItemTags(item_hash, android_id) {
//                getItemId(item_hash, android_id, (item_id) => {
//                    let sql = mysql.format(
//                            "SELECT ?? FROM ?? WHERE ?? = ?;",
//                            ["tag", "Tags", "item", item_id]
//                            );
//
//                    db.query(sql, (error, results, fields) => {
//                        if (error) {
//                            logSqlError(error);
//                        }
//                        //TODO emit
//                    });
//                });
//            }

            function pushItem(data, callback) {
                /*
                 * {
                 *    parent_hash: "blbalba"
                 *    android_id: "blabla"
                 * }
                 */
                
                //FIXME DEBUG
                console.log(address + "PUSH ITEM TO DB: ");
//                console.log(address + data);

                let sql = mysql.format(
                        "call add_item_by_hashes(?, ?, ?, FROM_UNIXTIME(?));",
                        [data.hash_name, data.android_id,
                            data.parent_hash, data.create_date]
                        );

                db.query(sql, (error, results, fields) => {
                    if (error && error.code !== "ER_DUP_ENTRY") {
                        logSqlError(error);
                    }

                    pushReview(data.hash_name, data.android_id,
                            data.review, data.rating, data.tags);

                    callback(error);
                });
            }

            function pushReview(item_hash, android_id, review, rating, tags) {
                getItemId(item_hash, android_id, (item_id) => {
                    if (item_id) {
                        let sql = mysql.format(
                                "call update_review(?, ?, ?);",
                                [item_id, review, rating]
                                );

                        db.query(sql, (error, results, fields) => {
                            if (!error) {

                                delItemTags(item_id, (error) => {
                                    if (!error) {
                                        pushTags(item_id, tags, (error) => {
                                            if (!error) {
                                                //tags added
                                                //TODO emit
                                            }
                                        });
                                    }
                                });

                            } else {
                                logSqlError(error);
                            }
                        });
                    } else {
                        //TODO emit
                        //item not found
                        //send error to client
                    }
                });
            }

            function deleteItem(item_hash, android_id, callback) {
                getItemId(item_hash, android_id, (item_id) => {
                    console.log(address + "DELETE ID:", item_id, " find: ", item_hash, " ", android_id);
                    
                    if(!item_id){
                        //"Item not found"
                        callback();
                    }
                    
                    let sql = mysql.format(
                            "call del_item(?);",
                            [item_id]
                            );

                    db.query(sql, (error, results, fields) => {
                        if (error) {
                            logSqlError(error);
                        }
                        callback(error);
                    });
                });
            }
        });
    });
}

//==========================================================================
//-------------------------------HELPERS------------------------------------

function logSqlError(error) {
    console.error("\nSQL query error!\ncode: %s\nerror message: %s\nsql: %s",
            error.code, error.sqlMessage, error.sql
            );
}
            
function delItemTags(item_id, callback) {
    let sql = mysql.format(
            "call del_item_tags(?);",
            [item_id]
            );

    db.query(sql, (error, results, fields) => {
        if (error) {
            logSqlError(error);
        }
        callback(error);
    });
}

function pushTags(item_id, tags, callback) {
    for (let i in tags) {
        let sql = mysql.format(
                "call add_tag(?,?);",
                [item_id, tags[i]]
                );

        db.query(sql, (error, results, fields) => {
            if (error) {
                logSqlError(error);
            }
            callback(error);
        });
    }

    if (tags.length === 0) {
        callback();
    }
}

function getItemId(item_hash, android_id, callback) {
    let sql = mysql.format(
            "SELECT find_item(?, ?) as item_id;",
            [item_hash, android_id]
            );

    db.query(sql, (error, results, fields) => {
        if (!error) {
            callback(results[0]['item_id']);
        } else {
            logSqlError(error);
            callback(null, error);
        }
    });
}

function getAllItems(page, callback) {
    let sql = mysql.format(
            "call select_all_items(?);",
            [page]
            );

    db.query(sql, (error, results, fields) => {
        if (!error) {
            //console.log("ALL ITEMS RAW:");
            //console.log(results);
            callback(results);
        } else {
            logSqlError(error);
            callback([], error);
        }
    });
}

function getItemsByDevice(device, callback) {
    let sql = mysql.format(
            "call select_items_by_device(?);",
            [device]
            );

    db.query(sql, (error, results, fields) => {
        if (!error) {
            callback(results);
        } else {
            logSqlError(error);
            callback([], error);
        }
    });
}

function getItemSubscribers(item_id, callback) {
    let sql = mysql.format(
            "call select_item_subscribers(?);",
            [item_id]
            );

    db.query(sql, (error, results, fields) => {
        if (!error) {
            callback(results);
        } else {
            logSqlError(error);
            callback([], error);
        }
    });
}

function getItemTags(item_id, callback) {
    let sql = mysql.format(
            "SELECT ?? FROM ?? WHERE ?? = ?;",
            ["tag", "Tags", "item", item_id]
            );

    db.query(sql, (error, results, fields) => {
        let rows = results;
        let tags = Array(rows.length);

        if (error) {
            logSqlError(error);
        } else {
            for (let i = 0; i < rows.length; ++i) {
                tags[i] = rows[i].tag;
            }
        }

        callback(tags, error);
    });
}


function makeRelations(items) {
    /*Return EXAMPLE:
     * [
     *   {
     *      item_hash: 'F67E1A45EC017F4A589C',
     *      creator: 'cccccccc',
     *      subscribers: ['aaaaaaaa', 'bbbbbbbb']
     *   },
     *   ...
     * ]
     */

    let relations = [];

    for (let i = 0; i < items.length; ++i) {
        let item = items[i];

        if (item.parent_item === null) {
            let subs = findSubscribers(items, item.id);

//            if (subs.length > 0) {
            relations.push({
                'item_hash': item.hash_name,
                'creator_device': item.android_id,
                'subscribers': subs
            });
//            }
        }
    }

    return relations;
}

function findParent(relations, subscrDevice) {
    for (let i = 0; i < relations.length; ++i) {

        let relation = relations[i];

        if(subscrDevice !== relation.creator_device){
            for (let j = 0; j < relation.subscribers.length; ++j) {
                if (subscrDevice === relation.subscribers[j]) {
                    return relation;
                }
            }
        }
    }

    return null;
}

function findSubscribers(items, parent) {
    let subscribers = [];

    for (let i = 0; i < items.length; ++i) {
        let item = items[i];

        if (item.parent_item === parent) {
            subscribers.push(item.android_id);
        }
    }

    return subscribers;
}

function findItemById(items, id) {
    for (let i = 0; i < items.length; ++i) {
        if (items[i].id === id) {
            return items[i];
        }
    }

    return null;
}

function arrayItemsUnique(array) {
    var a = array.concat();
    for(var i=0; i<a.length; ++i) {
        for(var j=i+1; j<a.length; ++j) {
            if(a[i].hash_name === a[j].hash_name &&
               a[i].android_id === a[j].android_id){
                a.splice(j--, 1);
            }
        }
    }

    return a;
}

function arrayRelsUnique(array) {
    var a = array.concat();
    for(var i=0; i<a.length; ++i) {
        for(var j=i+1; j<a.length; ++j) {
            if(a[i].item_hash === a[j].item_hash &&
               a[i].creator_device === a[j].creator_device){
                a.splice(j--, 1);
            }
        }
    }

    return a;
}


function addClient(clients, socket) {
    if (!(clients && socket)) {
        return;
    }

    clients[socket.id] = socket;
}

function removeClient(clients, socket) {
    if (!(clients && socket)) {
        return;
    }

    delete clients[socket.id];
}

start();