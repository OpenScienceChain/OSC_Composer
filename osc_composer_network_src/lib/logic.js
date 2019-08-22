/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Transaction Processor Functions for scientific data sharing network.
 */

'use strict';

// It's corresponding to the enum DataStatus in the model file.
const DataStatus = {
    AVAILABLE:  'AVAILABLE',
    NOT_AVAILABLE:   'NOTAVAILABLE'
};

/**
 * Processor function for DataReleaseTransaction
 * @param {org.osc.DataReleaseTransaction} tx
 * @transaction
 */
async function processDataReleaseTransaction(tx) {
    if (tx.data.status === DataStatus.AVAILABLE) {
        throw new Error('The data is already "AVAILABLE"');
    }
    checkCurrentParticipant(tx.provider, tx.data.provider);
    tx.data.status = DataStatus.AVAILABLE;
    await updateData(tx.data);
}

/**
 * Processor function for DataCallbackTransaction
 * @param {org.osc.DataCallbackTransaction} tx
 * @transaction
 */
async function processDataCallbackTransaction(tx) {
    checkCurrentParticipant(tx.provider, tx.data.provider);
    tx.date.status = DateStatus.NOT_AVAILABLE;
    await updateData(tx.data);
}

/**
 * Processor function for DataCommentTransaction
 * @param {org.osc.DataCommentTransaction} tx
 * @transaction
 */
async function processDataCommentTransaction(tx) {
    if (tx.data.status !== DataStatus.AVAILABLE) {
        throw new Error('You cannot comment a dataset which is not "AVAILABLE"');
    }

    if (tx.user == tx.data.provider) {
        throw new Error('You cannot comment a dataset belonging to yourself');
    }
    
    // Add data to user's active data list.
    if (tx.user.activeDataIds === undefined) {
        tx.user.activeDataIds = [];
    }
    tx.user.activeDataIds.push(tx.data.getFullyQualifiedIdentifier());

    await updateData(tx.data);
    await updateDataUser(tx.user);
}

function checkCurrentParticipant(p1, p2) {
    if(p1 === undefined || p2 === undefined) {
        throw new Error('There is at least one participant to be verified is null.');
    }
    if (p1.getFullyQualifiedIdentifier() !== p2.getFullyQualifiedIdentifier()) {
        throw new Error(`The participants ${p1.getFullyQualifiedIdentifier()} - ${p2.getFullyQualifiedIdentifier()} are not matched.`);
    }
}

/**
 * 
 * @param {org.osc.Data} data
 */
async function updateData(data) {
    // Get the asset registry for the datasets.
    const assetRegistry = await getAssetRegistry('org.osc.Data');
    // Update the data status in the data registry.
    // All data validating will be handled before calling this method.
    await assetRegistry.update(data);
}

/**
 * 
 * @param {org.osc.DataUser} dataUser
 */
async function updateDataUser(dataUser) {
    // Get the participant registry for the data users.
    const partRegistry = await getParticipantRegistry('org.osc.DataUser');
    // Update the data user in the registry.
    // All data validating will be handled before calling this method.
    await partRegistry.update(dataUser);
}
